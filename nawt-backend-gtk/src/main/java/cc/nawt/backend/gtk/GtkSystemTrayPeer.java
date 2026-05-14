package cc.nawt.backend.gtk;

import cc.nawt.spi.MenuPeer;
import cc.nawt.spi.SystemTrayConfig;
import cc.nawt.spi.SystemTrayPeer;

/**
 * GTK system tray, backed by a pure-Java {@link StatusNotifierItem}
 * implementation that speaks {@code org.kde.StatusNotifierItem} over
 * {@link GDBus} and exports its {@code GMenu} via
 * {@code g_dbus_connection_export_menu_model} (GLib does the dbusmenu
 * translation for us).
 *
 * <p>{@code Capability.SYSTEM_TRAY} is only set when the watcher
 * ({@code org.kde.StatusNotifierWatcher}) is registered on the session bus,
 * see {@link GtkPeerFactory#capabilities()}. On hosts without a watcher
 * (KDE plasma always has one; GNOME needs the AppIndicator extension),
 * constructing a {@link GtkSystemTrayPeer} still succeeds but the icon
 * never appears — the caller is expected to gate creation via
 * {@code Toolkit.supports(SYSTEM_TRAY)} instead.
 */
final class GtkSystemTrayPeer implements SystemTrayPeer {

    private final StatusNotifierItem sni;

    GtkSystemTrayPeer(SystemTrayConfig cfg) {
        java.lang.foreign.MemorySegment menuModel = java.lang.foreign.MemorySegment.NULL;
        java.lang.foreign.MemorySegment actionGroup = java.lang.foreign.MemorySegment.NULL;
        if (cfg.menu() instanceof GtkMenuPeer gmp) {
            menuModel = gmp.gmenu();
            actionGroup = GtkActions.group();
        }
        this.sni = new StatusNotifierItem(
            "nawt-tray", cfg.tooltip(), cfg.iconPath(), menuModel, actionGroup);
    }

    @Override public void setIconPath(String path) { sni.setIconNameOrPath(path); }

    @Override public void setTooltip(String tooltip) { sni.setTooltip(tooltip); }

    @Override public void setMenu(MenuPeer menu) {
        // Re-exporting the menu would require unexport + re-export on the SNI
        // and currently the SPI doesn't surface that. For tier-2, the menu
        // attached at construction time is the one that's exported.
        // Replacing menus mid-lifetime needs a follow-up.
        if (menu != null) {
            System.err.println(
                "[nawt] GtkSystemTrayPeer.setMenu after construction: re-export not "
                + "yet supported. Attach the menu via SystemTray.builder().menu(...) "
                + "before build().");
        }
    }

    @Override public void close() { sni.close(); }
}
