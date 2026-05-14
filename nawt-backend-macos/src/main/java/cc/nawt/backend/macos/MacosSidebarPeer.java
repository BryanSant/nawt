package cc.nawt.backend.macos;

import cc.nawt.spi.Peer;
import cc.nawt.spi.SidebarConfig;
import cc.nawt.spi.SidebarPeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Source-list-styled NSTableView wrapped in an NSScrollView. Each row's view
 * is supplied by the {@link cc.nawt.Sidebar Sidebar} widget façade (one
 * pre-built NSView per row, kept alive by the façade's row-widget references).
 */
final class MacosSidebarPeer implements SidebarPeer {

    /** NSTableViewStyleSourceList. */
    private static final long STYLE_SOURCE_LIST = 4L;
    /** NSTableViewSelectionHighlightStyleSourceList. */
    private static final long HIGHLIGHT_SOURCE_LIST = 1L;

    private static final MemoryLayout NSRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

    private final MemorySegment scrollView; // NSScrollView, retained
    private final MemorySegment tableView;  // NSTableView, retained
    private final MemorySegment dataSource; // NawtSidebarDataSource, retained
    private volatile List<MemorySegment> rowViews;
    private volatile IntConsumer trigger;

    MacosSidebarPeer(SidebarConfig cfg) {
        this.rowViews = extractViews(cfg.rowPeers());

        // NSTableView
        this.tableView = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSTableView")), Objc.sel("init"));
        // setStyle: NSTableViewStyleSourceList — vibrant inset look + accent highlight.
        Objc.sendVoidLong(tableView, Objc.sel("setStyle:"), STYLE_SOURCE_LIST);
        Objc.sendVoidLong(tableView, Objc.sel("setSelectionHighlightStyle:"), HIGHLIGHT_SOURCE_LIST);
        Objc.sendVoid(tableView, Objc.sel("setHeaderView:"), Objc.NIL);
        Objc.sendVoidBool(tableView, Objc.sel("setAllowsMultipleSelection:"), false);
        Objc.sendVoidBool(tableView, Objc.sel("setAllowsEmptySelection:"), true);
        // Automatic row heights driven by Auto Layout of the row's view.
        Objc.sendVoidBool(tableView, Objc.sel("setUsesAutomaticRowHeights:"), true);

        // Single column.
        MemorySegment column = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSTableColumn")),
            Objc.sel("initWithIdentifier:"), NSString.from("col"));
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(column, Objc.sel("setWidth:"), 220.0);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(tableView, Objc.sel("addTableColumn:"), column);
        Objc.sendVoid(column, Objc.sel("release"));

        // Data source / delegate (combined).
        this.dataSource = Objc.sendPtr(Delegates.newSidebarDataSource(), Objc.sel("retain"));
        Delegates.SIDEBAR_ROW_COUNT_PROVIDERS.put(dataSource.address(), () -> rowViews.size());
        Delegates.SIDEBAR_VIEW_PROVIDERS.put(dataSource.address(), i ->
            i >= 0 && i < rowViews.size() ? rowViews.get(i) : null);
        Delegates.SIDEBAR_SELECTION_HANDLERS.put(dataSource.address(), this::fireSelection);

        Objc.sendVoid(tableView, Objc.sel("setDataSource:"), dataSource);
        Objc.sendVoid(tableView, Objc.sel("setDelegate:"), dataSource);

        // NSScrollView wraps the table.
        try (var arena = Arena.ofConfined()) {
            MemorySegment frame = arena.allocate(NSRECT);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, 240.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, 400.0);
            MemorySegment alloc = Objc.send_alloc(Objc.cls("NSScrollView"));
            MemorySegment sv;
            try {
                sv = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, NSRECT))
                    .invoke(alloc, Objc.sel("initWithFrame:"), frame);
            } catch (Throwable t) { throw new RuntimeException(t); }
            this.scrollView = sv;
        }
        Objc.sendVoid(scrollView, Objc.sel("setDocumentView:"), tableView);
        Objc.sendVoidBool(scrollView, Objc.sel("setHasVerticalScroller:"), true);
        Objc.sendVoidBool(scrollView, Objc.sel("setHasHorizontalScroller:"), false);
        // NSBezelBorderTypeNone = 0 — sidebar style has no extra frame.
        Objc.sendVoidLong(scrollView, Objc.sel("setBorderType:"), 0L);
        Objc.sendVoidBool(scrollView, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        // setDrawsBackground:NO so the table's source-list vibrancy comes through.
        Objc.sendVoidBool(scrollView, Objc.sel("setDrawsBackground:"), false);

        Objc.sendVoid(tableView, Objc.sel("reloadData"));
        if (cfg.initialSelection() >= 0) {
            applySelection(cfg.initialSelection());
        }
    }

    /** Exposed for {@link MacosContainerPeer#peerView}. */
    MemorySegment view() { return scrollView; }

    private static List<MemorySegment> extractViews(List<Peer> peers) {
        List<MemorySegment> out = new ArrayList<>(peers.size());
        for (Peer p : peers) out.add(MacosContainerPeer.peerView(p));
        return out;
    }

    private void fireSelection() {
        IntConsumer t = trigger;
        if (t == null) return;
        long row = Objc.sendLong(tableView, Objc.sel("selectedRow"));
        try { t.accept((int) row); }
        catch (Throwable th) { th.printStackTrace(); }
    }

    private void applySelection(int index) {
        if (index < 0) {
            Objc.sendVoid(tableView, Objc.sel("deselectAll:"), Objc.NIL);
            return;
        }
        MemorySegment set;
        try {
            set = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSUINT))
                .invoke(Objc.cls("NSIndexSet"), Objc.sel("indexSetWithIndex:"), (long) index);
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR, Objc.BOOL))
                .invoke(tableView, Objc.sel("selectRowIndexes:byExtendingSelection:"), set, false);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void setRows(List<Peer> rowPeers) {
        this.rowViews = extractViews(rowPeers);
        Objc.sendVoid(tableView, Objc.sel("reloadData"));
    }

    @Override public int selectedIndex() {
        return (int) Objc.sendLong(tableView, Objc.sel("selectedRow"));
    }

    @Override public void setSelectedIndex(int index) { applySelection(index); }

    @Override public void onSelectionChange(IntConsumer trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.SIDEBAR_ROW_COUNT_PROVIDERS.remove(dataSource.address());
        Delegates.SIDEBAR_VIEW_PROVIDERS.remove(dataSource.address());
        Delegates.SIDEBAR_SELECTION_HANDLERS.remove(dataSource.address());
        Objc.sendVoid(tableView, Objc.sel("setDataSource:"), Objc.NIL);
        Objc.sendVoid(tableView, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(scrollView, Objc.sel("release"));
        Objc.sendVoid(tableView, Objc.sel("release"));
        Objc.sendVoid(dataSource, Objc.sel("release"));
    }
}
