package cc.nawt.backend.gtk;

import cc.nawt.spi.ListViewConfig;
import cc.nawt.spi.ListViewPeer;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * GtkListBox of label rows wrapped in a GtkScrolledWindow. GtkListBox is not
 * lazy-rendered (one row widget per item) which is fine for the modest sizes
 * a list view typically holds; for very large lists, GtkListView with a
 * GtkSelectionModel would be more appropriate.
 */
final class GtkListViewPeer implements ListViewPeer {

    private static final int GTK_SELECTION_SINGLE = 1;
    private static final int GTK_POLICY_AUTOMATIC = 1;
    private static final int GTK_POLICY_NEVER = 2;

    private final MemorySegment scrolled;     // GtkScrolledWindow*, retained — exposed as the view
    private final MemorySegment listBox;      // GtkListBox*, retained
    private List<String> items;
    private volatile IntConsumer trigger;

    GtkListViewPeer(ListViewConfig cfg) {
        this.items = cfg.items();

        MemorySegment box = Gtk.gtk_list_box_new();
        this.listBox = Gtk.g_object_ref(box);
        Gtk.gtk_list_box_set_selection_mode(listBox, GTK_SELECTION_SINGLE);

        MemorySegment sw = Gtk.gtk_scrolled_window_new();
        this.scrolled = Gtk.g_object_ref(sw);
        Gtk.gtk_scrolled_window_set_policy(scrolled, GTK_POLICY_NEVER, GTK_POLICY_AUTOMATIC);
        Gtk.gtk_scrolled_window_set_min_content_height(scrolled, cfg.visibleRowCount() * 24);
        Gtk.gtk_scrolled_window_set_child(scrolled, listBox);

        rebuildRows();
        if (cfg.initialSelection() >= 0 && cfg.initialSelection() < items.size()) {
            applySelection(cfg.initialSelection());
        }

        // GtkListBox emits "row-selected" with (GtkListBox*, GtkListBoxRow* or NULL, gpointer)
        // — that's a 3-arg signal. Re-use the void3 stub.
        GtkSignals.connectVoid3(listBox, "row-selected", this::fireSelection);
    }

    MemorySegment widget() { return scrolled; }

    private void rebuildRows() {
        // gtk_list_box_append wraps the child in an auto-generated GtkListBoxRow,
        // so the row — not the label — is the list box's direct child. Drain by
        // index until empty rather than tracking labels (which aren't children).
        MemorySegment row;
        while ((row = Gtk.gtk_list_box_get_row_at_index(listBox, 0)) != null && row.address() != 0) {
            Gtk.gtk_list_box_remove(listBox, row);
        }
        for (String item : items) {
            MemorySegment label = Gtk.gtk_label_new(item == null ? "" : item);
            Gtk.gtk_list_box_append(listBox, label);
        }
    }

    private void fireSelection() {
        IntConsumer t = trigger;
        if (t == null) return;
        try { t.accept(selectedIndex()); }
        catch (Throwable th) { th.printStackTrace(); }
    }

    private void applySelection(int index) {
        if (index < 0) {
            Gtk.gtk_list_box_unselect_all(listBox);
            return;
        }
        MemorySegment row = Gtk.gtk_list_box_get_row_at_index(listBox, index);
        if (row != null && row.address() != 0) {
            Gtk.gtk_list_box_select_row(listBox, row);
        }
    }

    @Override
    public void setItems(List<String> newItems) {
        this.items = newItems == null ? List.of() : List.copyOf(newItems);
        rebuildRows();
    }

    @Override
    public int selectedIndex() {
        MemorySegment row = Gtk.gtk_list_box_get_selected_row(listBox);
        if (row == null || row.address() == 0) return -1;
        return Gtk.gtk_list_box_row_get_index(row);
    }

    @Override
    public void setSelectedIndex(int index) { applySelection(index); }

    @Override
    public void onSelectionChange(IntConsumer trigger) { this.trigger = trigger; }

    @Override
    public void close() {
        MemorySegment row;
        while ((row = Gtk.gtk_list_box_get_row_at_index(listBox, 0)) != null && row.address() != 0) {
            Gtk.gtk_list_box_remove(listBox, row);
        }
        Gtk.g_object_unref(scrolled);
        Gtk.g_object_unref(listBox);
    }
}
