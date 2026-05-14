package cc.nawt.backend.gtk;

import cc.nawt.Toolkit;
import cc.nawt.spi.HeaderBarConfig;
import cc.nawt.spi.HeaderBarPeer;
import cc.nawt.spi.MenuActionConfig;
import cc.nawt.spi.Peer;
import cc.nawt.spi.PeerFactory;

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
            injectAboutItem(gm);
            MemorySegment btn = Gtk.gtk_menu_button_new();
            Gtk.gtk_menu_button_set_icon_name(btn, "open-menu-symbolic");
            Gtk.gtk_menu_button_set_menu_model(btn, gm.gmenu());
            Gtk.gtk_menu_button_set_primary(btn, true);
            // Resolve action references (e.g. "nawt.act_3") to the global
            // GtkActions group so menu items fire their Java callbacks.
            Gtk.gtk_widget_insert_action_group(btn, GtkActions.PREFIX, GtkActions.group());
            Adw.adw_header_bar_pack_end(widget, btn);
        } else if (cfg.menu() != null) {
            throw new IllegalArgumentException("Foreign MenuPeer: " + cfg.menu().getClass());
        }
    }

    /**
     * If the application has registered an About handler via
     * {@link Toolkit#onAbout(Runnable)}, append "About &lt;appName&gt;" to the burger
     * menu's GMenu as its own trailing section (rendered below a separator).
     * The user's {@code Menu} doesn't list About — the framework places it in
     * the platform-appropriate surface, matching how the macOS App menu does so
     * via {@code MacosMenuBarPeer.installAppMenu}.
     */
    private static void injectAboutItem(GtkMenuPeer gm) {
        PeerFactory pf = Toolkit.requireLaunched().peerFactory();
        if (!(pf instanceof GtkPeerFactory gpf)) return;
        Runnable handler = gpf.aboutHandler();
        if (handler == null) return;
        String appName = Gtk.g_get_application_name();
        String label = (appName == null || appName.isBlank()) ? "About" : "About " + appName;
        GtkMenuActionPeer action = (GtkMenuActionPeer) pf.createMenuAction(
            new MenuActionConfig(label, null, true));
        // Dispatch on a virtual thread — the peer's onSelect runs its Runnable
        // directly on the GTK signal callback (= UI thread). Handlers like
        // MessageDialog.show() block via CompletableFuture.join(), which would
        // deadlock the main loop. This matches MenuAction.dispatch's pattern.
        action.onSelect(() -> Thread.startVirtualThread(() -> {
            try { handler.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }));
        // GtkMenuSeparatorPeer is a section boundary in GtkMenuPeer.rebuild,
        // so appending {separator, action} yields the About in a fresh trailing
        // section — visually a separator above it inside the burger.
        gm.append(pf.createMenuSeparator());
        gm.append(action);
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
