package cc.nawt.backend.gtk;

import cc.nawt.spi.Alignment;
import cc.nawt.spi.ChildLayoutConfig;
import cc.nawt.spi.GridConfig;
import cc.nawt.spi.GridPeer;
import cc.nawt.spi.Peer;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

final class GtkGridPeer implements GridPeer {

    private final MemorySegment widget;
    private final boolean squareCells;
    private boolean squareApplied;
    private final List<Cell> cells = new ArrayList<>();

    private record Cell(MemorySegment widget, int colSpan, int rowSpan) {}

    GtkGridPeer(GridConfig cfg) {
        MemorySegment g = Gtk.gtk_grid_new();
        Gtk.gtk_grid_set_column_spacing(g, cfg.columnSpacing());
        Gtk.gtk_grid_set_row_spacing(g, cfg.rowSpacing());
        Gtk.gtk_grid_set_column_homogeneous(g, cfg.columnsHomogeneous());
        Gtk.gtk_grid_set_row_homogeneous(g, cfg.rowsHomogeneous());
        // columnCount / rowCount are advisory on GtkGrid — attach() grows the
        // grid as needed. We carry them in GridConfig for backends like
        // NSGridView that need explicit dimensions up front.
        this.widget = Gtk.g_object_ref(g);
        this.squareCells = cfg.squareCells();
        if (squareCells) {
            // Square cells need to know each child's natural sizes, which are
            // only computable once the widget tree is realized (the grid has
            // been attached to a window and added to the layout). Defer the
            // measure-and-size-request pass to "realize".
            GtkSignals.connectVoid(widget, "realize", this::applySquareCells);
        }
    }

    /**
     * Compute the per-cell square dimension and stamp it onto each non-spanning
     * child as a size-request. For each cell we take its natural width and
     * height normalized by its span (so the display Label in the calculator —
     * 4 cols × 1 row — contributes naturalWidth/4 and naturalHeight); cellDim
     * is the max across all those normalized values. Spanning children are
     * left unsized — the grid's homogeneous flags grow them to N×cellDim
     * automatically. Runs once, on first realize.
     */
    private void applySquareCells() {
        if (squareApplied) return;
        squareApplied = true;
        int cellDim = 0;
        for (Cell c : cells) {
            int natW = Gtk.gtk_widget_natural_size(c.widget(), Gtk.GTK_ORIENTATION_HORIZONTAL);
            int natH = Gtk.gtk_widget_natural_size(c.widget(), Gtk.GTK_ORIENTATION_VERTICAL);
            int perW = (natW + c.colSpan() - 1) / c.colSpan(); // ceil-div
            int perH = (natH + c.rowSpan() - 1) / c.rowSpan();
            if (perW > cellDim) cellDim = perW;
            if (perH > cellDim) cellDim = perH;
        }
        if (cellDim <= 0) return;
        for (Cell c : cells) {
            if (c.colSpan() == 1 && c.rowSpan() == 1) {
                Gtk.gtk_widget_set_size_request(c.widget(), cellDim, cellDim);
            }
        }
    }

    MemorySegment widget() { return widget; }

    @Override
    public void attach(Peer child, int column, int row, int columnSpan, int rowSpan, ChildLayoutConfig hints) {
        MemorySegment childWidget = GtkContainerPeer.peerWidget(child);

        // Default within-cell alignment is FILL (matches Row/Column STRETCH
        // default). Override per-child when hints.alignSelf() is set.
        int gtkAlign = hints.alignSelf() == null
            ? Gtk.GTK_ALIGN_FILL
            : toGtkAlign(hints.alignSelf());
        Gtk.gtk_widget_set_halign(childWidget, gtkAlign);
        Gtk.gtk_widget_set_valign(childWidget,
            hints.alignSelf() == Alignment.BASELINE ? Gtk.GTK_ALIGN_BASELINE : gtkAlign);

        if (hints.expand()) {
            Gtk.gtk_widget_set_hexpand(childWidget, true);
            Gtk.gtk_widget_set_vexpand(childWidget, true);
        }

        Gtk.gtk_grid_attach(widget, childWidget, column, row, columnSpan, rowSpan);
        if (squareCells) cells.add(new Cell(childWidget, columnSpan, rowSpan));
    }

    private static int toGtkAlign(Alignment a) {
        return switch (a) {
            case STRETCH -> Gtk.GTK_ALIGN_FILL;
            case START -> Gtk.GTK_ALIGN_START;
            case CENTER -> Gtk.GTK_ALIGN_CENTER;
            case END -> Gtk.GTK_ALIGN_END;
            case BASELINE -> Gtk.GTK_ALIGN_BASELINE;
        };
    }

    @Override
    public void close() { Gtk.g_object_unref(widget); }
}
