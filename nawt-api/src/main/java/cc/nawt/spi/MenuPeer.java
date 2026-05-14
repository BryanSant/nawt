package cc.nawt.spi;

/**
 * A native menu — used as both a top-level menu in a {@link MenuBarPeer} and
 * as a submenu nested under another {@link MenuPeer}.
 */
public non-sealed interface MenuPeer extends MenuItemPeer {
    void setTitle(String title);
    void append(MenuItemPeer item);
}
