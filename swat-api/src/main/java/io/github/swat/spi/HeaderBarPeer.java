package io.github.swat.spi;

/**
 * Peer for a window header bar. Implementations decide how to render packed
 * items in the start (leading) and end (trailing) regions of the host
 * window's chrome — {@code NSToolbar} on macOS, {@code AdwHeaderBar} on Linux.
 */
public non-sealed interface HeaderBarPeer extends Peer {
    /** Add a peer to the start (leading) region. Idempotent across reconfigures. */
    void addStart(Peer item);
    /** Add a peer to the end (trailing) region. */
    void addEnd(Peer item);
}
