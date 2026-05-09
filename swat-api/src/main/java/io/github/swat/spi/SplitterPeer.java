package io.github.swat.spi;

public non-sealed interface SplitterPeer extends Peer {
    void setStart(Peer child);
    void setEnd(Peer child);
}
