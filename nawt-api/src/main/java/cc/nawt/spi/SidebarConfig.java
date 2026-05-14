package cc.nawt.spi;

import java.util.List;

/**
 * @param rowPeers          one peer per row in display order; the peer's
 *                          underlying view is hosted directly in the source-list
 *                          cell. Caller owns the lifecycle (the {@code Sidebar}
 *                          widget releases them on close).
 * @param initialSelection  zero-based row to select after construction, or
 *                          {@code -1} for no initial selection.
 */
public record SidebarConfig(List<Peer> rowPeers, int initialSelection) {
    public SidebarConfig {
        rowPeers = rowPeers == null ? List.of() : List.copyOf(rowPeers);
    }

    public SidebarConfig(List<Peer> rowPeers) { this(rowPeers, -1); }
}
