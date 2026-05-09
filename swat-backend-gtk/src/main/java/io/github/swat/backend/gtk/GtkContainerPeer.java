package io.github.swat.backend.gtk;

import io.github.swat.spi.ContainerConfig;
import io.github.swat.spi.ContainerPeer;
import io.github.swat.spi.Orientation;
import io.github.swat.spi.Peer;

import java.lang.foreign.MemorySegment;

final class GtkContainerPeer implements ContainerPeer {

    private static final int GTK_ORIENTATION_HORIZONTAL = 0;
    private static final int GTK_ORIENTATION_VERTICAL = 1;

    private final MemorySegment widget;

    GtkContainerPeer(ContainerConfig cfg) {
        int orientation = cfg.orientation() == Orientation.VERTICAL
            ? GTK_ORIENTATION_VERTICAL : GTK_ORIENTATION_HORIZONTAL;
        MemorySegment box = Gtk.gtk_box_new(orientation, cfg.spacing());
        if (cfg.padding() > 0) {
            int p = cfg.padding();
            Gtk.gtk_widget_set_margin_top(box, p);
            Gtk.gtk_widget_set_margin_bottom(box, p);
            Gtk.gtk_widget_set_margin_start(box, p);
            Gtk.gtk_widget_set_margin_end(box, p);
        }
        this.widget = Gtk.g_object_ref(box);
    }

    MemorySegment widget() { return widget; }

    @Override
    public void append(Peer child) {
        Gtk.gtk_box_append(widget, peerWidget(child));
    }

    @Override
    public void close() { Gtk.g_object_unref(widget); }

    static MemorySegment peerWidget(Peer p) {
        return switch (p) {
            case GtkLabelPeer lp -> lp.widget();
            case GtkButtonPeer bp -> bp.widget();
            case GtkTextFieldPeer tp -> tp.widget();
            case GtkContainerPeer cp -> cp.widget();
            case GtkListViewPeer lv -> lv.widget();
            case GtkCheckboxPeer cb -> cb.widget();
            case GtkSwitchPeer sw -> sw.widget();
            case GtkRadioPeer rb -> rb.widget();
            case GtkSliderPeer sl -> sl.widget();
            case GtkProgressBarPeer pb -> pb.widget();
            case GtkSpinnerPeer sp -> sp.widget();
            case GtkDropDownPeer dd -> dd.widget();
            case GtkFramePeer fp -> fp.widget();
            case GtkScrollContainerPeer sc -> sc.widget();
            case GtkTabsPeer tp -> tp.widget();
            case GtkSplitterPeer spl -> spl.widget();
            case GtkExpanderPeer ex -> ex.widget();
            case GtkTreePeer tr -> tr.widget();
            case GtkImagePeer im -> im.widget();
            case GtkCanvasPeer cv -> cv.widget();
            case GtkWindowPeer wp -> throw new IllegalArgumentException(
                "Window cannot be added as a child widget");
            default -> throw new IllegalArgumentException(
                "Unknown peer type: " + p.getClass().getName());
        };
    }
}
