package cc.nawt.backend.gtk;

import cc.nawt.spi.Alignment;
import cc.nawt.spi.ChildLayoutConfig;
import cc.nawt.spi.ContainerConfig;
import cc.nawt.spi.ContainerPeer;
import cc.nawt.spi.Orientation;
import cc.nawt.spi.Peer;

import java.lang.foreign.MemorySegment;

final class GtkContainerPeer implements ContainerPeer {

    private static final int GTK_ORIENTATION_HORIZONTAL = 0;
    private static final int GTK_ORIENTATION_VERTICAL = 1;

    private final MemorySegment widget;
    private final boolean vertical;
    private final Alignment crossAxis;

    GtkContainerPeer(ContainerConfig cfg) {
        this.vertical = cfg.orientation() == Orientation.VERTICAL;
        this.crossAxis = cfg.crossAxis();
        int orientation = vertical ? GTK_ORIENTATION_VERTICAL : GTK_ORIENTATION_HORIZONTAL;
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
        append(child, ChildLayoutConfig.DEFAULT);
    }

    @Override
    public void append(Peer child, ChildLayoutConfig hints) {
        MemorySegment childWidget = peerWidget(child);
        Alignment effectiveAlign = hints.alignSelf() != null ? hints.alignSelf() : crossAxis;
        applyCrossAxisAlignment(childWidget, effectiveAlign);
        applyMainAxisExpand(childWidget, hints.expand());
        Gtk.gtk_box_append(widget, childWidget);
    }

    /**
     * Apply cross-axis alignment to the child. In a vertical box the cross
     * axis is horizontal, so {@code halign} controls it; in a horizontal box
     * the cross axis is vertical and {@code valign} controls it.
     * BASELINE only applies on the cross axis of a horizontal Row.
     */
    private void applyCrossAxisAlignment(MemorySegment child, Alignment align) {
        int gtkAlign = switch (align) {
            case STRETCH -> Gtk.GTK_ALIGN_FILL;
            case START -> Gtk.GTK_ALIGN_START;
            case CENTER -> Gtk.GTK_ALIGN_CENTER;
            case END -> Gtk.GTK_ALIGN_END;
            case BASELINE -> vertical ? Gtk.GTK_ALIGN_CENTER : Gtk.GTK_ALIGN_BASELINE;
        };
        if (vertical) {
            Gtk.gtk_widget_set_halign(child, gtkAlign);
        } else {
            Gtk.gtk_widget_set_valign(child, gtkAlign);
        }
    }

    /**
     * Mark the child as expanding along the main axis. GTK's expand flag
     * splits available slack equally among expanding siblings — which matches
     * the contract documented in LAYOUT.md (no per-child weight; equal split).
     */
    private void applyMainAxisExpand(MemorySegment child, boolean expand) {
        if (vertical) {
            Gtk.gtk_widget_set_vexpand(child, expand);
        } else {
            Gtk.gtk_widget_set_hexpand(child, expand);
        }
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
            case GtkGridPeer gp -> gp.widget();
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
