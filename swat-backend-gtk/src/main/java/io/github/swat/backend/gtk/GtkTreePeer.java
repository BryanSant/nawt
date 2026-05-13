package io.github.swat.backend.gtk;

import io.github.swat.spi.TreeConfig;
import io.github.swat.spi.TreeNodeData;
import io.github.swat.spi.TreePeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Tree on the modern GTK4 list-model stack: {@code GListStore} →
 * {@code GtkTreeListModel} → {@code GtkSingleSelection} → {@code GtkColumnView}.
 *
 * <p>Items are {@code GtkStringObject} payloads holding dotted path keys
 * ({@code ""}, {@code "0"}, {@code "0.1"}, …). The tree-list create-func
 * upcall builds a child {@code GListStore} for any non-leaf path; a single
 * {@code GtkSignalListItemFactory} handles {@code setup} (creates a
 * {@code GtkTreeExpander} wrapping a {@code GtkLabel}) and {@code bind}
 * (resolves the row's path back to its {@link TreeNodeData} label).
 */
final class GtkTreePeer implements TreePeer {

    private static final int GTK_POLICY_AUTOMATIC = 1;
    private static final int GTK_POLICY_NEVER = 2;

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1L);
    private static final ConcurrentHashMap<Long, GtkTreePeer> TREES = new ConcurrentHashMap<>();

    private static final MemorySegment SETUP_STUB;
    private static final MemorySegment BIND_STUB;
    private static final MemorySegment CREATE_MODEL_STUB;

    static {
        try {
            // void(self, GtkListItem*, user_data)
            MethodHandle setupMh = MethodHandles.lookup().findStatic(
                GtkTreePeer.class, "setupCallback",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            SETUP_STUB = Linker.nativeLinker().upcallStub(
                setupMh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);

            // void(self, GtkListItem*, user_data) — same shape as setup, kept separate
            // so its handler dispatches on the bind path.
            MethodHandle bindMh = MethodHandles.lookup().findStatic(
                GtkTreePeer.class, "bindCallback",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            BIND_STUB = Linker.nativeLinker().upcallStub(
                bindMh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);

            // GListModel*(gpointer item, gpointer user_data)
            MethodHandle createMh = MethodHandles.lookup().findStatic(
                GtkTreePeer.class, "createModelCallback",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class));
            CREATE_MODEL_STUB = Linker.nativeLinker().upcallStub(
                createMh,
                FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private final long token;
    private final MemorySegment scrolled;
    private final MemorySegment columnView;
    private final MemorySegment selection;
    private final MemorySegment treeListModel;
    private final MemorySegment rootStore;
    private volatile TreeNodeData root;
    private volatile Consumer<int[]> trigger;

    GtkTreePeer(TreeConfig cfg) {
        this.token = NEXT_TOKEN.getAndIncrement();
        this.root = cfg.root();
        TREES.put(token, this);

        long stringObjectType = Gtk.gtk_string_object_get_type();

        // Top-level model: a GListStore with one GtkStringObject for the user's root.
        this.rootStore = Gtk.g_list_store_new(stringObjectType);
        MemorySegment rootObj = Gtk.gtk_string_object_new("");
        Gtk.g_list_store_append(rootStore, rootObj);
        Gtk.g_object_unref(rootObj);

        // Tree list model: passthrough=false (rows are GtkTreeListRow), autoexpand=true.
        this.treeListModel = Gtk.gtk_tree_list_model_new(
            rootStore, false, true,
            CREATE_MODEL_STUB, MemorySegment.ofAddress(token), MemorySegment.NULL);

        this.selection = Gtk.gtk_single_selection_new(treeListModel);
        // gtk_single_selection_new takes ownership of the model ref; the
        // selection itself is what we keep.

        // Factory wires setup + bind via signals; both stubs receive (factory, listitem, token).
        MemorySegment factory = Gtk.gtk_signal_list_item_factory_new();
        MemorySegment data = MemorySegment.ofAddress(token);
        Gtk.g_signal_connect_data(factory, "setup", SETUP_STUB, data, MemorySegment.NULL, 0);
        Gtk.g_signal_connect_data(factory, "bind", BIND_STUB, data, MemorySegment.NULL, 0);

        MemorySegment column = Gtk.gtk_column_view_column_new(null, factory);
        Gtk.gtk_column_view_column_set_expand(column, true);

        MemorySegment cv = Gtk.gtk_column_view_new(selection);
        this.columnView = Gtk.g_object_ref(cv);
        Gtk.gtk_column_view_set_show_column_separators(columnView, false);
        Gtk.gtk_column_view_append_column(columnView, column);
        Gtk.g_object_unref(column);

        MemorySegment sw = Gtk.gtk_scrolled_window_new();
        this.scrolled = Gtk.g_object_ref(sw);
        Gtk.gtk_scrolled_window_set_policy(scrolled, GTK_POLICY_NEVER, GTK_POLICY_AUTOMATIC);
        Gtk.gtk_scrolled_window_set_min_content_height(scrolled, 240);
        Gtk.gtk_scrolled_window_set_child(scrolled, columnView);

        // Selection notification: "notify::selected" fires whenever the
        // selected position property changes.
        GtkSignals.connectVoid3(selection, "notify::selected", this::fireSelection);
    }

    MemorySegment widget() { return scrolled; }

    /* ---------- callbacks (shared, dispatched via token) ---------- */

    @SuppressWarnings("unused")
    private static void setupCallback(MemorySegment factory, MemorySegment listitem,
                                      MemorySegment userData) {
        try {
            MemorySegment expander = Gtk.gtk_tree_expander_new();
            MemorySegment label = Gtk.gtk_label_new("");
            Gtk.gtk_tree_expander_set_child(expander, label);
            Gtk.gtk_list_item_set_child(listitem, expander);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @SuppressWarnings("unused")
    private static void bindCallback(MemorySegment factory, MemorySegment listitem,
                                     MemorySegment userData) {
        try {
            GtkTreePeer peer = TREES.get(userData.address());
            if (peer == null) return;
            MemorySegment treeListRow = Gtk.gtk_list_item_get_item(listitem);
            if (treeListRow == null || treeListRow.address() == 0) return;
            MemorySegment expander = Gtk.gtk_list_item_get_child(listitem);
            if (expander == null || expander.address() == 0) return;
            Gtk.gtk_tree_expander_set_list_row(expander, treeListRow);

            MemorySegment strObj = Gtk.gtk_tree_list_row_get_item(treeListRow);
            if (strObj == null || strObj.address() == 0) return;
            String pathKey = Gtk.gtk_string_object_get_string(strObj);
            // gtk_tree_list_row_get_item returns a (transfer full) reference;
            // release it now that we have the path string.
            Gtk.g_object_unref(strObj);

            TreeNodeData node = peer.resolve(pathKey);
            String text = node == null ? "" : node.label();

            MemorySegment label = Gtk.gtk_tree_expander_get_child(expander);
            if (label != null && label.address() != 0) {
                Gtk.gtk_label_set_text(label, text);
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    @SuppressWarnings("unused")
    private static MemorySegment createModelCallback(MemorySegment item, MemorySegment userData) {
        try {
            GtkTreePeer peer = TREES.get(userData.address());
            if (peer == null) return MemorySegment.NULL;
            String parentPath = Gtk.gtk_string_object_get_string(item);
            TreeNodeData node = peer.resolve(parentPath);
            if (node == null || node.children().isEmpty()) return MemorySegment.NULL;

            MemorySegment store = Gtk.g_list_store_new(Gtk.gtk_string_object_get_type());
            for (int i = 0; i < node.children().size(); i++) {
                String childPath = parentPath == null || parentPath.isEmpty()
                    ? Integer.toString(i)
                    : parentPath + "." + i;
                MemorySegment child = Gtk.gtk_string_object_new(childPath);
                Gtk.g_list_store_append(store, child);
                Gtk.g_object_unref(child);
            }
            return store;
        } catch (Throwable t) { t.printStackTrace(); return MemorySegment.NULL; }
    }

    /* ---------- path resolution ---------- */

    TreeNodeData resolve(String pathKey) {
        TreeNodeData node = root;
        if (node == null) return null;
        if (pathKey == null || pathKey.isEmpty()) return node;
        for (String seg : pathKey.split("\\.")) {
            int idx;
            try { idx = Integer.parseInt(seg); }
            catch (NumberFormatException e) { return null; }
            if (idx < 0 || idx >= node.children().size()) return null;
            node = node.children().get(idx);
        }
        return node;
    }

    private static String pathKey(int[] path) {
        if (path == null || path.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(path[i]);
        }
        return sb.toString();
    }

    private static int[] parsePath(String key) {
        if (key == null || key.isEmpty()) return new int[0];
        String[] parts = key.split("\\.");
        int[] path = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { path[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { return null; }
        }
        return path;
    }

    /* ---------- selection wiring ---------- */

    private static final int GTK_INVALID_LIST_POSITION = 0xFFFFFFFF;

    private void fireSelection() {
        Consumer<int[]> t = trigger;
        if (t == null) return;
        int[] path = selectedPath();
        try { t.accept(path); }
        catch (Throwable th) { th.printStackTrace(); }
    }

    private String selectedItemPath() {
        int pos = Gtk.gtk_single_selection_get_selected(selection);
        if (pos == GTK_INVALID_LIST_POSITION) return null;
        MemorySegment treeListRow = Gtk.g_list_model_get_item(selection, pos);
        if (treeListRow == null || treeListRow.address() == 0) return null;
        try {
            MemorySegment strObj = Gtk.gtk_tree_list_row_get_item(treeListRow);
            if (strObj == null || strObj.address() == 0) return null;
            try { return Gtk.gtk_string_object_get_string(strObj); }
            finally { Gtk.g_object_unref(strObj); }
        } finally {
            Gtk.g_object_unref(treeListRow);
        }
    }

    @Override public void setRoot(TreeNodeData root) {
        this.root = root;
        // Replace the top-level GListStore contents and rebuild from there.
        // The tree-list model observes items-changed on rootStore and rebuilds
        // child rows by invoking createModelCallback again.
        Gtk.g_list_store_remove_all(rootStore);
        MemorySegment rootObj = Gtk.gtk_string_object_new("");
        Gtk.g_list_store_append(rootStore, rootObj);
        Gtk.g_object_unref(rootObj);
    }

    @Override public int[] selectedPath() {
        String key = selectedItemPath();
        if (key == null) return null;
        return parsePath(key);
    }

    @Override public void selectPath(int[] path) {
        if (path == null) {
            Gtk.gtk_single_selection_set_selected(selection, GTK_INVALID_LIST_POSITION);
            return;
        }
        String target = pathKey(path);
        // Linear scan over the visible tree. autoexpand=true keeps every
        // expandable row visible, so any valid path is reachable without an
        // explicit expand step.
        int n = Gtk.g_list_model_get_n_items(selection);
        for (int i = 0; i < n; i++) {
            MemorySegment row = Gtk.g_list_model_get_item(selection, i);
            if (row == null || row.address() == 0) continue;
            try {
                MemorySegment strObj = Gtk.gtk_tree_list_row_get_item(row);
                if (strObj == null || strObj.address() == 0) continue;
                try {
                    if (target.equals(Gtk.gtk_string_object_get_string(strObj))) {
                        Gtk.gtk_single_selection_set_selected(selection, i);
                        return;
                    }
                } finally { Gtk.g_object_unref(strObj); }
            } finally { Gtk.g_object_unref(row); }
        }
        Gtk.gtk_single_selection_set_selected(selection, GTK_INVALID_LIST_POSITION);
    }

    @Override public void onSelectionChange(Consumer<int[]> trigger) { this.trigger = trigger; }

    @Override public void close() {
        TREES.remove(token);
        // gtk_column_view_new, gtk_single_selection_new, and gtk_tree_list_model_new
        // are all (transfer full) on their model arguments — selection, treeListModel,
        // and rootStore are now owned by the column view's reference chain. We only
        // unref what we explicitly ref'd in the constructor (scrolled, columnView).
        Gtk.g_object_unref(scrolled);
        Gtk.g_object_unref(columnView);
    }
}
