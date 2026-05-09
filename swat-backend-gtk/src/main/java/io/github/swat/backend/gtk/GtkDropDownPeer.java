package io.github.swat.backend.gtk;

import io.github.swat.spi.DropDownConfig;
import io.github.swat.spi.DropDownPeer;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.IntConsumer;

final class GtkDropDownPeer implements DropDownPeer {

    private static final int GTK_INVALID_LIST_POSITION = 0xFFFFFFFF;

    private final MemorySegment widget;
    private volatile IntConsumer trigger;
    private volatile int lastSelected;

    GtkDropDownPeer(DropDownConfig cfg) {
        MemorySegment w = Gtk.gtk_drop_down_new_from_strings(cfg.items());
        this.widget = Gtk.g_object_ref(w);
        if (cfg.initialSelection() >= 0) {
            Gtk.gtk_drop_down_set_selected(widget, cfg.initialSelection());
            this.lastSelected = cfg.initialSelection();
        } else {
            Gtk.gtk_drop_down_set_selected(widget, GTK_INVALID_LIST_POSITION);
            this.lastSelected = -1;
        }
        GtkSignals.connectVoid3(widget, "notify::selected", () -> {
            int now = selectedIndex();
            if (now == lastSelected) return;
            lastSelected = now;
            IntConsumer t = trigger;
            if (t != null) {
                try { t.accept(now); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment widget() { return widget; }

    @Override public void setItems(List<String> items) {
        MemorySegment model = Gtk.gtk_string_list_new(items == null ? List.of() : items);
        Gtk.gtk_drop_down_set_model(widget, model);
        Gtk.g_object_unref(model);
    }

    @Override public int selectedIndex() {
        int s = Gtk.gtk_drop_down_get_selected(widget);
        return s == GTK_INVALID_LIST_POSITION ? -1 : s;
    }

    @Override public void setSelectedIndex(int index) {
        lastSelected = index;
        Gtk.gtk_drop_down_set_selected(widget, index < 0 ? GTK_INVALID_LIST_POSITION : index);
    }

    @Override public void onSelectionChange(IntConsumer trigger) { this.trigger = trigger; }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
