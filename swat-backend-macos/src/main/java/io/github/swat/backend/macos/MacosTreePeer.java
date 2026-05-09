package io.github.swat.backend.macos;

import io.github.swat.spi.TreeConfig;
import io.github.swat.spi.TreeNodeData;
import io.github.swat.spi.TreePeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Consumer;

/**
 * Tree backed by NSOutlineView with native expand/collapse and disclosure
 * triangles. Items are NSString path keys ("", "0", "0.1", …) — NSOutlineView
 * uses {@code -isEqual:} to identify items, so identical-content NSStrings
 * are treated as the same node across reloads.
 */
final class MacosTreePeer implements TreePeer {

    private static final MemoryLayout NSRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

    private final MemorySegment scrollView;
    private final MemorySegment outlineView;
    private final MemorySegment dataSource;
    private final MemorySegment column;
    private volatile TreeNodeData root;
    private volatile Consumer<int[]> trigger;

    MacosTreePeer(TreeConfig cfg) {
        this.root = cfg.root();

        this.outlineView = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSOutlineView")), Objc.sel("init"));

        this.column = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSTableColumn")),
            Objc.sel("initWithIdentifier:"), NSString.from("col"));
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(column, Objc.sel("setWidth:"), 240.0);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(outlineView, Objc.sel("addTableColumn:"), column);
        // The outline column controls indentation; set it to our only column.
        Objc.sendVoid(outlineView, Objc.sel("setOutlineTableColumn:"), column);

        Objc.sendVoid(outlineView, Objc.sel("setHeaderView:"), Objc.NIL);
        Objc.sendVoidBool(outlineView, Objc.sel("setAllowsMultipleSelection:"), false);
        Objc.sendVoidBool(outlineView, Objc.sel("setAllowsEmptySelection:"), true);

        this.dataSource = Objc.sendPtr(Delegates.newOutlineDataSource(), Objc.sel("retain"));
        Delegates.OUTLINE_RESOLVERS.put(dataSource.address(), this::resolve);
        Delegates.OUTLINE_SELECTION_HANDLERS.put(dataSource.address(), this::fireSelection);

        Objc.sendVoid(outlineView, Objc.sel("setDataSource:"), dataSource);
        Objc.sendVoid(outlineView, Objc.sel("setDelegate:"), dataSource);

        try (var arena = Arena.ofConfined()) {
            MemorySegment frame = arena.allocate(NSRECT);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, 240.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, 240.0);
            MemorySegment alloc = Objc.send_alloc(Objc.cls("NSScrollView"));
            MemorySegment sv;
            try {
                sv = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, NSRECT))
                    .invoke(alloc, Objc.sel("initWithFrame:"), frame);
            } catch (Throwable t) { throw new RuntimeException(t); }
            this.scrollView = sv;
        }
        Objc.sendVoid(scrollView, Objc.sel("setDocumentView:"), outlineView);
        Objc.sendVoidBool(scrollView, Objc.sel("setHasVerticalScroller:"), true);
        Objc.sendVoidLong(scrollView, Objc.sel("setBorderType:"), 2L);
        Objc.sendVoidBool(scrollView, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        Objc.sendVoid(outlineView, Objc.sel("reloadData"));
        // Expand the user's root row so its immediate children are visible by
        // default — matches the prior flat-list look-and-feel.
        MemorySegment rootKey = NSString.from("");
        Objc.sendVoid(outlineView, Objc.sel("expandItem:"), rootKey);
    }

    MemorySegment view() { return scrollView; }

    /** Resolve a path key ("", "0", "0.1") to the corresponding TreeNodeData, or null. */
    private TreeNodeData resolve(String pathKey) {
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

    private void fireSelection() {
        Consumer<int[]> t = trigger;
        if (t == null) return;
        int[] path = selectedPath();
        try { t.accept(path); }
        catch (Throwable th) { th.printStackTrace(); }
    }

    @Override public void setRoot(TreeNodeData root) {
        this.root = root;
        Objc.sendVoid(outlineView, Objc.sel("reloadData"));
        Objc.sendVoid(outlineView, Objc.sel("expandItem:"), NSString.from(""));
    }

    @Override public int[] selectedPath() {
        long row = Objc.sendLong(outlineView, Objc.sel("selectedRow"));
        if (row < 0) return null;
        MemorySegment item;
        try {
            item = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT))
                .invoke(outlineView, Objc.sel("itemAtRow:"), row);
        } catch (Throwable t) { throw new RuntimeException(t); }
        if (item == null || item.address() == 0) return null;
        return parsePath(NSString.toJava(item));
    }

    @Override public void selectPath(int[] path) {
        if (path == null) {
            Objc.sendVoid(outlineView, Objc.sel("deselectAll:"), Objc.NIL);
            return;
        }
        // Expand every ancestor along the path so the row exists.
        // Ancestors: the user's root ("") plus path[0..length-2].
        Objc.sendVoid(outlineView, Objc.sel("expandItem:"), NSString.from(""));
        StringBuilder ancestor = new StringBuilder();
        for (int i = 0; i < path.length - 1; i++) {
            if (i > 0) ancestor.append('.');
            ancestor.append(path[i]);
            Objc.sendVoid(outlineView, Objc.sel("expandItem:"),
                NSString.from(ancestor.toString()));
        }

        MemorySegment key = NSString.from(pathKey(path));
        long row;
        try {
            row = (long) Objc.msgSend(FunctionDescriptor.of(
                    Objc.NSINT, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(outlineView, Objc.sel("rowForItem:"), key);
        } catch (Throwable t) { throw new RuntimeException(t); }
        if (row < 0) {
            Objc.sendVoid(outlineView, Objc.sel("deselectAll:"), Objc.NIL);
            return;
        }
        MemorySegment set;
        try {
            set = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSUINT))
                .invoke(Objc.cls("NSIndexSet"), Objc.sel("indexSetWithIndex:"), row);
        } catch (Throwable t) { throw new RuntimeException(t); }
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR, Objc.BOOL))
                .invoke(outlineView, Objc.sel("selectRowIndexes:byExtendingSelection:"), set, false);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void onSelectionChange(Consumer<int[]> trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.OUTLINE_RESOLVERS.remove(dataSource.address());
        Delegates.OUTLINE_SELECTION_HANDLERS.remove(dataSource.address());
        Objc.sendVoid(outlineView, Objc.sel("setDataSource:"), Objc.NIL);
        Objc.sendVoid(outlineView, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(scrollView, Objc.sel("release"));
        Objc.sendVoid(outlineView, Objc.sel("release"));
        Objc.sendVoid(column, Objc.sel("release"));
        Objc.sendVoid(dataSource, Objc.sel("release"));
    }
}
