package cc.nawt.spi;

/** Either {@code path} or {@code data} is set; the other is null/empty. */
public record ImageConfig(String path, byte[] data) {
    public ImageConfig {
        if (path == null && data == null) path = "";
    }
}
