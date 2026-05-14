package cc.nawt.spi;

public non-sealed interface ContainerPeer extends Peer {
    /** Appends a child with default layout hints. */
    void append(Peer child);

    /**
     * Appends a child with explicit layout hints. Default implementation
     * ignores the hints and falls back to {@link #append(Peer)} so existing
     * backends remain source-compatible; backends should override to honor
     * {@link ChildLayoutConfig#expand()} and {@link ChildLayoutConfig#alignSelf()}.
     */
    default void append(Peer child, ChildLayoutConfig hints) {
        append(child);
    }
}
