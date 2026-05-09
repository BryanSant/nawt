package io.github.swat.spi;

public non-sealed interface LabelPeer extends Peer {
    void setText(String text);
    String getText();
}
