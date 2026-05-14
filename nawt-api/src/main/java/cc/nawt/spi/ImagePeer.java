package cc.nawt.spi;

import cc.nawt.ClipShape;

public non-sealed interface ImagePeer extends Peer {
    void setPath(String path);
    void setData(byte[] data);
    void setClipShape(ClipShape shape);
}
