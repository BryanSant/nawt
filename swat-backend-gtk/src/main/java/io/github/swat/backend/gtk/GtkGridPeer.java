package io.github.swat.backend.gtk;

import io.github.swat.spi.Alignment;
import io.github.swat.spi.ChildLayoutConfig;
import io.github.swat.spi.GridConfig;
import io.github.swat.spi.GridPeer;
import io.github.swat.spi.Peer;

import java.lang.foreign.MemorySegment;

final class GtkGridPeer implements GridPeer {

    private final MemorySegment widget;

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
