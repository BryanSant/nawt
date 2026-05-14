package cc.nawt.spi;

import cc.nawt.ClipShape;

/** Either {@code path} or {@code data} is set; the other is null/empty. */
public record ImageConfig(String path, byte[] data, ClipShape clipShape) {
    public ImageConfig {
        if (path == null && data == null) path = "";
    }

    public ImageConfig(String path, byte[] data) {
        this(path, data, null);
    }
}
