package io.github.swat.backend.gtk;

import io.github.swat.spi.HeaderBarPeer;
import io.github.swat.spi.MenuBarPeer;
import io.github.swat.spi.Peer;
import io.github.swat.spi.WindowConfig;
import io.github.swat.spi.WindowPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.BooleanSupplier;

/**
 * Window backed by {@code AdwWindow} containing an {@code AdwToolbarView}.
 * Top bars (menu bar and {@code AdwHeaderBar}) live in the toolbar view's
 * top-bar stack; the user's content is the toolbar view's content child.
 *
 * <p>Every window has chrome: if the application doesn't supply a
 * {@link HeaderBarPeer}, the window uses an empty default
 * {@code AdwHeaderBar} that surfaces the {@code GtkWindow} title and
 * close/minimize controls.
 */
final class GtkWindowPeer implements WindowPeer {

    private final MemorySegment widget;            // AdwWindow (extends GtkWindow)
    private final MemorySegment toolbarView;       // AdwToolbarView
    private final MemorySegment toastOverlay;      // AdwToastOverlay — wraps user content
    private final MemorySegment defaultHeader;     // AdwHeaderBar — restored when setHeaderBar(null)
    private MemorySegment menuBarChild;            // current MenuBar widget, or null
    private MemorySegment currentHeader;           // currently-mounted header (default or user-supplied)
    private MemorySegment mountedMenu;             // null if menu not currently in the toolbar
    private MemorySegment mountedHeader;           // null if header not currently in the toolbar
    private volatile BooleanSupplier permitClose;

    GtkWindowPeer(WindowConfig cfg) {
        Adw.adw_init();
        MemorySegment w = Adw.adw_window_new();
        Gtk.gtk_window_set_title(w, cfg.title());
        Gtk.gtk_window_set_default_size(w, cfg.width(), cfg.height());

        MemorySegment tv = Adw.adw_toolbar_view_new();
        Adw.adw_window_set_content(w, tv);

        // The toolbar view's content is an AdwToastOverlay so toast() can
        // surface transient messages on top of whatever the application
        // installed via setContent.
        MemorySegment overlay = Adw.adw_toast_overlay_new();
        Adw.adw_toolbar_view_set_content(tv, overlay);

        // Default empty header bar; retained so setHeaderBar(null) can restore it.
        MemorySegment dh = Adw.adw_header_bar_new();
        this.defaultHeader = Gtk.g_object_ref(dh);
        this.currentHeader = defaultHeader;

        this.widget = Gtk.g_object_ref(w);
        this.toolbarView = tv;
        this.toastOverlay = overlay;

        mountTopBars();
        GtkSignals.connectBool(widget, "close-request", this::shouldVetoClose);
    }

    private boolean shouldVetoClose() {
        BooleanSupplier h = permitClose;
        if (h == null) return false;
        try { return !h.getAsBoolean(); }
        catch (Throwable t) { t.printStackTrace(); return false; }
    }

    @Override public void setTitle(String title) { Gtk.gtk_window_set_title(widget, title); }

    @Override public void setSize(int width, int height) {
        Gtk.gtk_window_set_default_size(widget, width, height);
    }

    @Override public void setContent(Peer content) {
        Adw.adw_toast_overlay_set_child(toastOverlay, GtkContainerPeer.peerWidget(content));
    }

    @Override public void toast(String message, int timeoutMs) {
        MemorySegment toast = Adw.adw_toast_new(message == null ? "" : message);
        // AdwToast timeout is in seconds (guint); 0 means "no timeout".
        int seconds = Math.max(1, timeoutMs / 1000);
        Adw.adw_toast_set_timeout(toast, seconds);
        // The overlay takes ownership; we do not need to unref.
        Adw.adw_toast_overlay_add_toast(toastOverlay, toast);
    }

    @Override
    public void setMenuBar(MenuBarPeer bar) {
        menuBarChild = bar == null ? null : ((GtkMenuBarPeer) bar).widget();
        mountTopBars();
    }

    @Override
    public void setHeaderBar(HeaderBarPeer bar) {
        if (bar == null) {
            currentHeader = defaultHeader;
        } else {
            if (!(bar instanceof GtkHeaderBarPeer ghp)) {
                throw new IllegalArgumentException("Foreign HeaderBarPeer: " + bar.getClass());
            }
            currentHeader = ghp.widget();
        }
        mountTopBars();
    }

    /**
     * Detach all currently-mounted top bars and re-attach in canonical order:
     * menu first (top of the stack), then header. {@code add_top_bar} appends,
     * so the order of calls determines the visual top-to-bottom order.
     */
    private void mountTopBars() {
        if (mountedMenu != null) {
            Adw.adw_toolbar_view_remove(toolbarView, mountedMenu);
            mountedMenu = null;
        }
        if (mountedHeader != null) {
            Adw.adw_toolbar_view_remove(toolbarView, mountedHeader);
            mountedHeader = null;
        }
        if (menuBarChild != null) {
            Adw.adw_toolbar_view_add_top_bar(toolbarView, menuBarChild);
            mountedMenu = menuBarChild;
        }
        if (currentHeader != null) {
            Adw.adw_toolbar_view_add_top_bar(toolbarView, currentHeader);
            mountedHeader = currentHeader;
        }
    }

    @Override public void show() { Gtk.gtk_window_present(widget); }

    @Override public void onCloseRequest(BooleanSupplier permitClose) { this.permitClose = permitClose; }

    @Override public void close() {
        Gtk.gtk_window_destroy(widget);
        Gtk.g_object_unref(widget);
        Gtk.g_object_unref(defaultHeader);
    }
}
