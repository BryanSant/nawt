package io.github.swat.backend.gtk;

import io.github.swat.spi.HeaderBarConfig;
import io.github.swat.spi.HeaderBarPeer;
import io.github.swat.spi.Peer;

import java.lang.foreign.MemorySegment;

/**
 * Header bar backed by {@code AdwHeaderBar}. The window installs this widget
 * via {@link GtkWindowPeer#setHeaderBar} as a top bar of an
 * {@code AdwToolbarView}; the bar reads the host {@code GtkWindow}'s title
 * automatically when no title widget is set explicitly.
 */
final class GtkHeaderBarPeer implements HeaderBarPeer {

    private final MemorySegment widget;

    GtkHeaderBarPeer(HeaderBarConfig cfg) {
        MemorySegment hb = Adw.adw_header_bar_new();
        this.widget = Gtk.g_object_ref(hb);
        for (Peer p : cfg.startItems()) addStart(p);
        for (Peer p : cfg.endItems()) addEnd(p);
    }

    MemorySegment widget() { return widget; }

    @Override public void addStart(Peer item) {
        Adw.adw_header_bar_pack_start(widget, GtkContainerPeer.peerWidget(item));
    }

    @Override public void addEnd(Peer item) {
        Adw.adw_header_bar_pack_end(widget, GtkContainerPeer.peerWidget(item));
    }

    @Override public void close() {
        Gtk.g_object_unref(widget);
    }
}
