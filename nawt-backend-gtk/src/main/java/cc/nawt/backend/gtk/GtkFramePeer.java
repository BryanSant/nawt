package cc.nawt.backend.gtk;

import cc.nawt.spi.FrameConfig;
import cc.nawt.spi.FramePeer;
import cc.nawt.spi.Peer;

import java.lang.foreign.MemorySegment;

final class GtkFramePeer implements FramePeer {

    private final MemorySegment widget;

    GtkFramePeer(FrameConfig cfg) {
        MemorySegment w = Gtk.gtk_frame_new(cfg.title().isEmpty() ? null : cfg.title());
        this.widget = Gtk.g_object_ref(w);
    }

    MemorySegment widget() { return widget; }

    @Override public void setTitle(String title) {
        Gtk.gtk_frame_set_label(widget, (title == null || title.isEmpty()) ? null : title);
    }

    @Override public void setContent(Peer content) {
        Gtk.gtk_frame_set_child(widget, GtkContainerPeer.peerWidget(content));
    }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
