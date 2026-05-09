package io.github.swat.backend.gtk;

import io.github.swat.spi.Peer;
import io.github.swat.spi.ScrollContainerConfig;
import io.github.swat.spi.ScrollContainerPeer;

import java.lang.foreign.MemorySegment;

final class GtkScrollContainerPeer implements ScrollContainerPeer {

    private static final int GTK_POLICY_AUTOMATIC = 1;
    private static final int GTK_POLICY_NEVER = 2;

    private final MemorySegment widget;

    GtkScrollContainerPeer(ScrollContainerConfig cfg) {
        MemorySegment w = Gtk.gtk_scrolled_window_new();
        this.widget = Gtk.g_object_ref(w);
        int h = cfg.horizontal() ? GTK_POLICY_AUTOMATIC : GTK_POLICY_NEVER;
        int v = cfg.vertical() ? GTK_POLICY_AUTOMATIC : GTK_POLICY_NEVER;
        Gtk.gtk_scrolled_window_set_policy(widget, h, v);
    }

    MemorySegment widget() { return widget; }

    @Override public void setContent(Peer content) {
        Gtk.gtk_scrolled_window_set_child(widget, GtkContainerPeer.peerWidget(content));
    }

    @Override public void close() { Gtk.g_object_unref(widget); }
}
