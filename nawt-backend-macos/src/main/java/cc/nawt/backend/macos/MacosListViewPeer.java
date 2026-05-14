package cc.nawt.backend.macos;

import cc.nawt.spi.ListViewConfig;
import cc.nawt.spi.ListViewPeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * macOS list view: NSTableView wrapped in NSScrollView, single column, no
 * header. A custom data source supplies row count and per-row NSString values
 * from a Java-side list; selection changes are forwarded via the data source's
 * {@code tableViewSelectionDidChange:} method.
 */
final class MacosListViewPeer implements ListViewPeer {

    private static final MemoryLayout NSRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

    private final MemorySegment scrollView;  // NSScrollView*, retained, exposed as the view
    private final MemorySegment tableView;   // NSTableView*, retained
    private final MemorySegment dataSource;  // NawtTableDataSource*, retained
    private volatile List<String> items;
    private volatile IntConsumer trigger;

    MacosListViewPeer(ListViewConfig cfg) {
        this.items = cfg.items();

        // NSTableView alloc + init
        this.tableView = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSTableView")), Objc.sel("init"));

        // Add a single column
        MemorySegment column = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSTableColumn")),
            Objc.sel("initWithIdentifier:"), NSString.from("col"));
        // setWidth: takes CGFloat
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(column, Objc.sel("setWidth:"), 200.0);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(tableView, Objc.sel("addTableColumn:"), column);
        Objc.sendVoid(column, Objc.sel("release"));

        // Hide column header
        Objc.sendVoid(tableView, Objc.sel("setHeaderView:"), Objc.NIL);
        // Single selection
        Objc.sendVoidBool(tableView, Objc.sel("setAllowsMultipleSelection:"), false);
        Objc.sendVoidBool(tableView, Objc.sel("setAllowsEmptySelection:"), true);

        // Data source / delegate
        this.dataSource = Objc.sendPtr(Delegates.newTableDataSource(), Objc.sel("retain"));
        Delegates.TABLE_ROW_COUNT_PROVIDERS.put(dataSource.address(), () -> this.items.size());
        Delegates.TABLE_VALUE_PROVIDERS.put(dataSource.address(), i ->
            i >= 0 && i < this.items.size() ? this.items.get(i) : null);
        Delegates.TABLE_SELECTION_HANDLERS.put(dataSource.address(), this::fireSelection);

        Objc.sendVoid(tableView, Objc.sel("setDataSource:"), dataSource);
        Objc.sendVoid(tableView, Objc.sel("setDelegate:"), dataSource);

        // Wrap in NSScrollView for scrolling
        try (var arena = Arena.ofConfined()) {
            MemorySegment frame = arena.allocate(NSRECT);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, 200.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, (double) (cfg.visibleRowCount() * 22));

            MemorySegment alloc = Objc.send_alloc(Objc.cls("NSScrollView"));
            MemorySegment sv;
            try {
                sv = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, NSRECT))
                    .invoke(alloc, Objc.sel("initWithFrame:"), frame);
            } catch (Throwable t) { throw new RuntimeException(t); }
            this.scrollView = sv; // alloc+init = +1
        }
        Objc.sendVoid(scrollView, Objc.sel("setDocumentView:"), tableView);
        Objc.sendVoidBool(scrollView, Objc.sel("setHasVerticalScroller:"), true);
        Objc.sendVoidBool(scrollView, Objc.sel("setHasHorizontalScroller:"), false);
        // NSBezelBorder = 2
        Objc.sendVoidLong(scrollView, Objc.sel("setBorderType:"), 2L);
        Objc.sendVoidBool(scrollView, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // Reload to pick up initial items
        Objc.sendVoid(tableView, Objc.sel("reloadData"));
        if (cfg.initialSelection() >= 0) {
            applySelection(cfg.initialSelection());
        }
    }

    /** The view to insert into a container — the scrollview hosts the tableview. */
    MemorySegment view() { return scrollView; }

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
        // [NSIndexSet indexSetWithIndex:(NSUInteger)index]
        MemorySegment set = (MemorySegment) invokeUL(
            Objc.cls("NSIndexSet"), Objc.sel("indexSetWithIndex:"), (long) index);
        // [tableView selectRowIndexes:set byExtendingSelection:NO]
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR, Objc.BOOL))
                .invoke(tableView, Objc.sel("selectRowIndexes:byExtendingSelection:"), set, false);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private static Object invokeUL(MemorySegment target, MemorySegment sel, long arg) {
        try {
            return Objc.msgSend(FunctionDescriptor.of(Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSUINT))
                .invoke(target, sel, arg);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override
    public void setItems(List<String> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        Objc.sendVoid(tableView, Objc.sel("reloadData"));
    }

    @Override
    public int selectedIndex() {
        long row = Objc.sendLong(tableView, Objc.sel("selectedRow"));
        return (int) row;
    }

    @Override
    public void setSelectedIndex(int index) { applySelection(index); }

    @Override
    public void onSelectionChange(IntConsumer trigger) { this.trigger = trigger; }

    @Override
    public void close() {
        Delegates.TABLE_ROW_COUNT_PROVIDERS.remove(dataSource.address());
        Delegates.TABLE_VALUE_PROVIDERS.remove(dataSource.address());
        Delegates.TABLE_SELECTION_HANDLERS.remove(dataSource.address());
        Objc.sendVoid(tableView, Objc.sel("setDataSource:"), Objc.NIL);
        Objc.sendVoid(tableView, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(scrollView, Objc.sel("release"));
        Objc.sendVoid(tableView, Objc.sel("release"));
        Objc.sendVoid(dataSource, Objc.sel("release"));
    }
}
