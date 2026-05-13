package io.github.swat.backend.macos;

import io.github.swat.spi.Alignment;
import io.github.swat.spi.ChildLayoutConfig;
import io.github.swat.spi.GridConfig;
import io.github.swat.spi.GridPeer;
import io.github.swat.spi.Peer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class MacosGridPeer implements GridPeer {

    private static final MemoryLayout NS_RANGE = MemoryLayout.structLayout(
        ValueLayout.JAVA_LONG.withName("location"),
        ValueLayout.JAVA_LONG.withName("length"));

    /** NSGridCellPlacement enum values (NSGridView.h). */
    private static final long PLACEMENT_LEADING = 2L;
    private static final long PLACEMENT_TRAILING = 3L;
    private static final long PLACEMENT_CENTER = 4L;
    private static final long PLACEMENT_FILL = 5L;
    /** NSGridRowAlignmentFirstBaseline. */
    private static final long ROW_ALIGN_FIRST_BASELINE = 2L;

    private final MemorySegment view; // NSGridView, retained
    private final boolean columnsHomogeneous;
    private final boolean rowsHomogeneous;
    private final boolean squareCells;

    /** First non-spanning cell's content view, used as the equal-width anchor. */
    private MemorySegment widthReference;
    /** First non-spanning cell's content view, used as the equal-height anchor. */
    private MemorySegment heightReference;

    MacosGridPeer(GridConfig cfg) {
        this.columnsHomogeneous = cfg.columnsHomogeneous();
        this.rowsHomogeneous = cfg.rowsHomogeneous();
        this.squareCells = cfg.squareCells();

        MemorySegment v;
        try {
            v = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT, Objc.NSINT))
                .invoke(Objc.cls("NSGridView"),
                    Objc.sel("gridViewWithNumberOfColumns:rows:"),
                    (long) cfg.columnCount(), (long) cfg.rowCount());
        } catch (Throwable t) { throw new RuntimeException(t); }

        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setColumnSpacing:"), (double) cfg.columnSpacing());
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setRowSpacing:"), (double) cfg.rowSpacing());
        } catch (Throwable t) { throw new RuntimeException(t); }

        Objc.sendVoidLong(v, Objc.sel("setXPlacement:"), PLACEMENT_FILL);
        Objc.sendVoidLong(v, Objc.sel("setYPlacement:"), PLACEMENT_FILL);

        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // Hug intrinsic height. Grid is form-layout-like vertically: when its
        // parent has more space than its rows need, the slack should stay in
        // the parent, not get redistributed into rows that aren't constrained
        // by a homogeneous-equality rule (e.g. spanning header rows would
        // otherwise absorb all of it and look oversized). Horizontal hugging
        // stays at the default low priority so the grid stretches to fill its
        // parent's width — necessary for homogeneous columns to actually
        // span the available width.
        MacosContainerPeer.setContentHuggingPriority(v, 1000, 1L); // vertical

        this.view = Objc.sendPtr(v, Objc.sel("retain"));
    }

    MemorySegment view() { return view; }

    @Override
    public void attach(Peer child, int column, int row, int columnSpan, int rowSpan, ChildLayoutConfig hints) {
        MemorySegment childView = MacosContainerPeer.peerView(child);

        if (columnSpan > 1 || rowSpan > 1) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment h = arena.allocate(NS_RANGE);
                h.setAtIndex(ValueLayout.JAVA_LONG, 0, column);
                h.setAtIndex(ValueLayout.JAVA_LONG, 1, columnSpan);
                MemorySegment vRange = arena.allocate(NS_RANGE);
                vRange.setAtIndex(ValueLayout.JAVA_LONG, 0, row);
                vRange.setAtIndex(ValueLayout.JAVA_LONG, 1, rowSpan);
                try {
                    Objc.msgSend(FunctionDescriptor.ofVoid(
                            Objc.PTR, Objc.PTR, NS_RANGE, NS_RANGE))
                        .invoke(view, Objc.sel("mergeCellsInHorizontalRange:verticalRange:"),
                            h, vRange);
                } catch (Throwable t) { throw new RuntimeException(t); }
            }
        }

        MemorySegment cell;
        try {
            cell = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT, Objc.NSINT))
                .invoke(view, Objc.sel("cellAtColumnIndex:rowIndex:"),
                    (long) column, (long) row);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(cell, Objc.sel("setContentView:"), childView);

        if (hints.alignSelf() != null) {
            applyCellAlignment(cell, hints.alignSelf());
        }

        if (hints.expand()) {
            MacosContainerPeer.setContentHuggingPriority(childView, 100, 0L);
            MacosContainerPeer.setContentHuggingPriority(childView, 100, 1L);
        }

        // Homogeneous: NSGridView has no native flag, so we add Auto Layout
        // equality constraints between each non-spanning content view and the
        // first one we saw. With the grid's default placement of Fill, content
        // widths/heights track their cell sizes, so equal content sizes ⇒
        // equal column/row sizes. Spanning cells are excluded because their
        // size spans multiple columns/rows.
        boolean nonSpanning = columnSpan == 1 && rowSpan == 1;
        if (nonSpanning) {
            if (columnsHomogeneous) {
                if (widthReference == null) {
                    widthReference = childView;
                } else if (widthReference.address() != childView.address()) {
                    addEqualAnchorConstraint(childView, widthReference, "widthAnchor");
                }
            }
            if (rowsHomogeneous) {
                if (heightReference == null) {
                    heightReference = childView;
                } else if (heightReference.address() != childView.address()) {
                    addEqualAnchorConstraint(childView, heightReference, "heightAnchor");
                }
            }
            // Square cells: constrain each cell's widthAnchor == heightAnchor.
            // Combined with homogeneous constraints, all cells become equal
            // squares; on its own, each cell becomes individually square at
            // its own size.
            if (squareCells) {
                addSquareConstraint(childView);
            }
        }
    }

    /** Add an active required-priority {@code view.widthAnchor == view.heightAnchor} constraint. */
    private static void addSquareConstraint(MemorySegment view) {
        MemorySegment widthAnchor = Objc.sendPtr(view, Objc.sel("widthAnchor"));
        MemorySegment heightAnchor = Objc.sendPtr(view, Objc.sel("heightAnchor"));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(widthAnchor, Objc.sel("constraintEqualToAnchor:"), heightAnchor);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    /**
     * Add an active required-priority constraint of the form
     * {@code a.<anchor> == b.<anchor>}. Used to enforce homogeneous sizing
     * across non-spanning grid cells.
     */
    private static void addEqualAnchorConstraint(MemorySegment a, MemorySegment b, String anchorName) {
        MemorySegment anchorA = Objc.sendPtr(a, Objc.sel(anchorName));
        MemorySegment anchorB = Objc.sendPtr(b, Objc.sel(anchorName));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(anchorA, Objc.sel("constraintEqualToAnchor:"), anchorB);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    private static void applyCellAlignment(MemorySegment cell, Alignment a) {
        switch (a) {
            case STRETCH -> {
                Objc.sendVoidLong(cell, Objc.sel("setXPlacement:"), PLACEMENT_FILL);
                Objc.sendVoidLong(cell, Objc.sel("setYPlacement:"), PLACEMENT_FILL);
            }
            case START -> {
                Objc.sendVoidLong(cell, Objc.sel("setXPlacement:"), PLACEMENT_LEADING);
                Objc.sendVoidLong(cell, Objc.sel("setYPlacement:"), PLACEMENT_LEADING);
            }
            case CENTER -> {
                Objc.sendVoidLong(cell, Objc.sel("setXPlacement:"), PLACEMENT_CENTER);
                Objc.sendVoidLong(cell, Objc.sel("setYPlacement:"), PLACEMENT_CENTER);
            }
            case END -> {
                Objc.sendVoidLong(cell, Objc.sel("setXPlacement:"), PLACEMENT_TRAILING);
                Objc.sendVoidLong(cell, Objc.sel("setYPlacement:"), PLACEMENT_TRAILING);
            }
            case BASELINE ->
                Objc.sendVoidLong(cell, Objc.sel("setRowAlignment:"), ROW_ALIGN_FIRST_BASELINE);
        }
    }

    @Override
    public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
