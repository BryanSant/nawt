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
        // Pack the burger menu last so it sits at the trailing edge, just
        // inside the window-control close button (Adwaita convention).
        if (cfg.menu() instanceof GtkMenuPeer gm) {
            MemorySegment btn = Gtk.gtk_menu_button_new();
            Gtk.gtk_menu_button_set_icon_name(btn, "open-menu-symbolic");
            Gtk.gtk_menu_button_set_menu_model(btn, gm.gmenu());
            Gtk.gtk_menu_button_set_primary(btn, true);
            // Resolve action references (e.g. "swat.act_3") to the global
            // GtkActions group so menu items fire their Java callbacks.
            Gtk.gtk_widget_insert_action_group(btn, GtkActions.PREFIX, GtkActions.group());
            Adw.adw_header_bar_pack_end(widget, btn);
        } else if (cfg.menu() != null) {
            throw new IllegalArgumentException("Foreign MenuPeer: " + cfg.menu().getClass());
        }
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
