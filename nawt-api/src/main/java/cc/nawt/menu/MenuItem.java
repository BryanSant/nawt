package cc.nawt.menu;

import cc.nawt.spi.MenuItemPeer;

/**
 * Sealed root of the menu-item hierarchy. Distinct from {@link cc.nawt.Widget}
 * because menu items live in NSMenu / GMenu structures, not the visual layout.
 */
public sealed interface MenuItem extends AutoCloseable
    permits Menu, MenuAction, MenuSeparator {

    MenuItemPeer peer();

    @Override
    void close();
}
