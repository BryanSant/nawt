package io.github.swat.spi;

public non-sealed interface FramePeer extends Peer {
    void setTitle(String title);
    void setContent(Peer content);
}
