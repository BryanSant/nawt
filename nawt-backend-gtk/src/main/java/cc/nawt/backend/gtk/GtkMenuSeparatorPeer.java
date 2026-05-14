package cc.nawt.backend.gtk;

import cc.nawt.spi.MenuSeparatorPeer;

/**
 * GTK has no first-class menu-item separator; visual separators come from
 * grouping items into {@code g_menu_append_section} sections. This peer is a
 * pure marker — the parent {@link GtkMenuPeer} switches sections when it
 * encounters one.
 */
final class GtkMenuSeparatorPeer implements MenuSeparatorPeer {

    GtkMenuSeparatorPeer() {}

    @Override public void close() {}
}
