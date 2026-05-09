package io.github.swat.spi;

public non-sealed interface ImagePeer extends Peer {
    void setPath(String path);
    void setData(byte[] data);
}
