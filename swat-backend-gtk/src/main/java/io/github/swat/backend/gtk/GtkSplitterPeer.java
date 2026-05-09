package io.github.swat.backend.gtk;

import io.github.swat.spi.Orientation;
import io.github.swat.spi.Peer;
import io.github.swat.spi.SplitterConfig;
import io.github.swat.spi.SplitterPeer;

import java.lang.foreign.MemorySegment;

final class GtkSplitterPeer implements SplitterPeer {

    private static final int GTK_ORIENTATION_HORIZONTAL = 0;
    private static final int GTK_ORIENTATION_VERTICAL = 1;

    private final MemorySegment widget;

    GtkSplitterPeer(SplitterConfig cfg) {
        int o = cfg.orientation() == Orientation.HORIZONTAL
            ? GTK_ORIENTATION_HORIZONTAL : GTK_ORIENTATION_VERTICAL;
        MemorySegment w = Gtk.gtk_paned_new(o);
        this.widget = Gtk.g_object_ref(w);
        if (cfg.position() > 0) Gtk.gtk_paned_set_position(widget, cfg.position());
    }

    MemorySegment widget() { return widget; }

    @Override public void setStart(Peer child) {
        Gtk.gtk_paned_set_start_child(widget, GtkContainerPeer.peerWidget(child));
    }

    @Override public void setEnd(Peer child) {
        Gtk.gtk_paned_set_end_child(widget, GtkContainerPeer.peerWidget(child));
    }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
