package cc.nawt;

import cc.nawt.spi.ImageConfig;
import cc.nawt.spi.ImagePeer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class Image implements Widget {

    private final ImagePeer peer;

    private Image(ImagePeer peer) { this.peer = peer; }

    public static Image fromFile(String path) {
        return Ui.onUi(() -> {
            ImagePeer p = Toolkit.requireLaunched().peerFactory()
                .createImage(new ImageConfig(path, null, null));
            return new Image(p);
        });
    }

    public static Image fromBytes(byte[] data) {
        return Ui.onUi(() -> {
            ImagePeer p = Toolkit.requireLaunched().peerFactory()
                .createImage(new ImageConfig(null, data, null));
            return new Image(p);
        });
    }

    /**
     * Load an image from a classpath resource. {@code anchor} is the class
     * whose classloader and package are used to resolve {@code path} — pass
     * a class from the calling module to make resource resolution work
     * across JPMS module boundaries. {@code path} follows the usual
     * {@link Class#getResourceAsStream} rules: a leading {@code /} is
     * absolute on the classpath; otherwise the path is resolved relative
     * to {@code anchor}'s package.
     */
    public static Image fromResource(Class<?> anchor, String path) {
        byte[] bytes;
        try (InputStream is = anchor.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException(
                    "Resource not found: " + path + " (anchor=" + anchor.getName() + ")");
            }
            bytes = is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return fromBytes(bytes);
    }

    public Image path(String path) {
        Ui.runOnUi(() -> peer.setPath(path));
        return this;
    }

    public Image data(byte[] data) {
        Ui.runOnUi(() -> peer.setData(data));
        return this;
    }

    /**
     * Mask the image to the given shape. Pass {@code null} to remove an
     * existing clip. Requires {@link Capability#IMAGE_CLIP} on the active
     * backend — backends without it will throw {@code UnsupportedOperationException}.
     */
    public Image clipShape(ClipShape shape) {
        Ui.runOnUi(() -> peer.setClipShape(shape));
        return this;
    }

    /**
     * Constrain the image's rendered size to {@code (width, height)} points.
     * Both must be {@code >= 0}; pass {@code 0} on either axis to release the
     * constraint and let the host's intrinsic-size logic decide.
     */
    public Image size(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("size dimensions must be >= 0");
        }
        Ui.runOnUi(() -> peer.setSize(width, height));
        return this;
    }

    @Override public Image tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Image dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Image acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public ImagePeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
