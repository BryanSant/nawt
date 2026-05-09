package io.github.swat.spi;

public non-sealed interface ContainerPeer extends Peer {
    void append(Peer child);
}
