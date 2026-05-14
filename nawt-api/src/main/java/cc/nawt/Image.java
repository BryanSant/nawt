package cc.nawt;

import cc.nawt.spi.ImageConfig;
import cc.nawt.spi.ImagePeer;

public final class Image implements Widget {

    private final ImagePeer peer;

    private Image(ImagePeer peer) { this.peer = peer; }

    public static Image fromFile(String path) {
        return Ui.onUi(() -> {
            ImagePeer p = Toolkit.requireLaunched().peerFactory()
                .createImage(new ImageConfig(path, null));
            return new Image(p);
        });
    }

    public static Image fromBytes(byte[] data) {
        return Ui.onUi(() -> {
            ImagePeer p = Toolkit.requireLaunched().peerFactory()
                .createImage(new ImageConfig(null, data));
            return new Image(p);
        });
    }

    public Image path(String path) {
        Ui.runOnUi(() -> peer.setPath(path));
        return this;
    }

    public Image data(byte[] data) {
        Ui.runOnUi(() -> peer.setData(data));
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
