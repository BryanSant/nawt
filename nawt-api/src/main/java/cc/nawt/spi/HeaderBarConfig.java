package cc.nawt.spi;

import java.util.List;

/**
 * Configuration for a window header bar. Items are pre-built peers (typically
 * Buttons, Labels, or Switches) packed into the start (leading) and end
 * (trailing) regions of the bar. The bar shows no title of its own — the host
 * Window's title is used (NSToolbar unified style on macOS,
 * {@code AdwWindowTitle} fallback on Adwaita).
 */
public record HeaderBarConfig(List<Peer> startItems, List<Peer> endItems, MenuPeer menu) {
    public HeaderBarConfig {
        startItems = startItems == null ? List.of() : List.copyOf(startItems);
        endItems = endItems == null ? List.of() : List.copyOf(endItems);
    }

    public HeaderBarConfig(List<Peer> startItems, List<Peer> endItems) {
        this(startItems, endItems, null);
    }
}
