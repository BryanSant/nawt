package cc.nawt.spi;

import cc.nawt.ClipShape;

public non-sealed interface ImagePeer extends Peer {
    void setPath(String path);
    void setData(byte[] data);
    void setClipShape(ClipShape shape);

    /**
     * Constrain the image view to {@code (width, height)} points via Auto
     * Layout (macOS) or {@code gtk_widget_set_size_request} (GTK). Pass
     * {@code 0} on either axis to release that axis's constraint.
     */
    void setSize(int width, int height);
}
