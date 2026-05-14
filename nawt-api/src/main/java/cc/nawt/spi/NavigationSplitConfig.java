package cc.nawt.spi;

/**
 * @param sidebar               leading pane peer (typically a {@link SidebarPeer})
 * @param detail                trailing pane peer
 * @param sidebarPreferredWidth points; {@code 0} = use the host default
 * @param sidebarMinWidth       points; {@code 0} = use the host default
 */
public record NavigationSplitConfig(
    Peer sidebar,
    Peer detail,
    int sidebarPreferredWidth,
    int sidebarMinWidth) {

    public NavigationSplitConfig(Peer sidebar, Peer detail) {
        this(sidebar, detail, 0, 0);
    }
}
