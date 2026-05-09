package io.github.swat.spi;

public non-sealed interface SpinnerPeer extends Peer {
    void setActive(boolean active);
    boolean isActive();
}
