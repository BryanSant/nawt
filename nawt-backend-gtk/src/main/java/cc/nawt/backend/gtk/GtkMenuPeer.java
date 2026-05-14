package cc.nawt.backend.gtk;

import cc.nawt.spi.MenuConfig;
import cc.nawt.spi.MenuItemPeer;
import cc.nawt.spi.MenuPeer;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * GMenu wrapper. Children are accumulated in a Java list; the GMenu model is
 * rebuilt after each {@link #append} so that {@link GtkMenuSeparatorPeer}s are
 * realized as section boundaries (which GTK renders as visual separators).
 */
final class GtkMenuPeer implements MenuPeer {

    private final MemorySegment gmenu; // GMenu*, owned by us via g_object_ref
    private final List<MenuItemPeer> items = new ArrayList<>();
    private volatile String title;

    GtkMenuPeer(MenuConfig cfg) {
        this.title = cfg.title() == null ? "" : cfg.title();
        this.gmenu = Gtk.g_menu_new();
    }

    MemorySegment gmenu() { return gmenu; }
    String title() { return title; }

    @Override
    public void setTitle(String t) {
        this.title = t == null ? "" : t;
        // Top-level title is owned by the parent MenuBar/Menu's append; we don't relabel here.
    }

    @Override
    public void append(MenuItemPeer item) {
        items.add(item);
        rebuild();
    }

    private void rebuild() {
        Gtk.g_menu_remove_all(gmenu);

        MemorySegment section = Gtk.g_menu_new();
        boolean sectionEmpty = true;

        for (MenuItemPeer item : items) {
            if (item instanceof GtkMenuSeparatorPeer) {
                if (!sectionEmpty) {
                    Gtk.g_menu_append_section(gmenu, null, section);
                    Gtk.g_object_unref(section);
                    section = Gtk.g_menu_new();
                    sectionEmpty = true;
                }
            } else if (item instanceof GtkMenuActionPeer a) {
                Gtk.g_menu_append(section, a.label(), a.detailedAction());
                sectionEmpty = false;
            } else if (item instanceof GtkMenuPeer sub) {
                Gtk.g_menu_append_submenu(section, sub.title(), sub.gmenu);
                sectionEmpty = false;
            } else {
                throw new IllegalArgumentException("Unknown menu item peer: " + item.getClass());
            }
        }
        if (!sectionEmpty) {
            Gtk.g_menu_append_section(gmenu, null, section);
        }
        Gtk.g_object_unref(section);
    }

    @Override
    public void close() {
        Gtk.g_object_unref(gmenu);
    }
}
