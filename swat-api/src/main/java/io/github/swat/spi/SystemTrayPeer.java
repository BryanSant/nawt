package io.github.swat.spi;

/**
 * Peer for a system tray / status icon. Multiple tray peers per app are
 * permitted (matches {@code NSStatusItem} on macOS, {@code StatusNotifierItem}
 * on Linux).
 */
public non-sealed interface SystemTrayPeer extends Peer {
    /** Replace the icon. Pass {@code null} to clear. */
    void setIconPath(String path);

    /** Replace the hover tooltip. Pass {@code null} to clear. */
    void setTooltip(String tooltip);

    /** Attach a menu (right-click on macOS, primary-click on Linux).
     *  Pass {@code null} to detach. */
    void setMenu(MenuPeer menu);
}
