package cc.nawt.backend.gtk;

import cc.nawt.spi.MenuBarPeer;
import cc.nawt.spi.MenuPeer;

import java.lang.foreign.MemorySegment;

/**
 * Builds a GMenu of top-level submenus and lazily realizes a
 * {@code GtkPopoverMenuBar} widget on first {@link #widget() request}.
 * The action group is installed on the widget so that menu-item activations
 * resolve to {@link GtkActions} entries.
 */
final class GtkMenuBarPeer implements MenuBarPeer {

    private final MemorySegment rootModel; // GMenu*, retained
    private MemorySegment widget;          // GtkPopoverMenuBar*, retained on first widget() call

    GtkMenuBarPeer() {
        this.rootModel = Gtk.g_menu_new();
    }

    @Override
    public void addMenu(MenuPeer menu) {
        if (!(menu instanceof GtkMenuPeer gm)) {
            throw new IllegalArgumentException("Foreign MenuPeer: " + menu.getClass());
        }
        Gtk.g_menu_append_submenu(rootModel, gm.title(), gm.gmenu());
    }

    /** Realize and return the menu-bar widget. Idempotent; safe to call after {@link #addMenu} mutations. */
    MemorySegment widget() {
        if (widget == null) {
            MemorySegment w = Gtk.gtk_popover_menu_bar_new_from_model(rootModel);
            this.widget = Gtk.g_object_ref(w);
            Gtk.gtk_widget_insert_action_group(widget, GtkActions.PREFIX, GtkActions.group());
        }
        return widget;
    }

    @Override
    public void close() {
        if (widget != null) {
            Gtk.g_object_unref(widget);
            widget = null;
        }
        Gtk.g_object_unref(rootModel);
    }
}
