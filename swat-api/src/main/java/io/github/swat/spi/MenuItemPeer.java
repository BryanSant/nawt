package io.github.swat.spi;

/**
 * Marker peer hierarchy for native menu objects (menu actions, separators,
 * submenus). Distinct from {@link Peer} because menu items are not visual
 * widgets in the layout tree — they live in NSMenu / GMenu structures.
 */
public sealed interface MenuItemPeer extends AutoCloseable
    permits MenuActionPeer, MenuSeparatorPeer, MenuPeer {

    @Override
    void close();
}
