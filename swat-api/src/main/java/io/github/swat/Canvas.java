package io.github.swat;

import io.github.swat.spi.CanvasConfig;
import io.github.swat.spi.CanvasPeer;

import java.util.function.Consumer;

/** Custom-drawn fixed-size surface. */
public final class Canvas implements Widget {

    private final CanvasPeer peer;
    private volatile Consumer<Painter> painter;

    private Canvas(CanvasPeer peer) {
        this.peer = peer;
        peer.onPaint(p -> {
            Consumer<Painter> cb = painter;
            if (cb != null) {
                try { cb.accept(p); }
                catch (Throwable t) { t.printStackTrace(); }
            }
        });
    }

    public static Canvas of(int width, int height) {
        return Ui.onUi(() -> {
            CanvasPeer p = Toolkit.requireLaunched().peerFactory()
                .createCanvas(new CanvasConfig(width, height));
            return new Canvas(p);
        });
    }

    public Canvas onPaint(Consumer<Painter> painter) {
        this.painter = painter;
        Ui.runOnUi(peer::invalidate);
        return this;
    }

    public Canvas invalidate() {
        Ui.runOnUi(peer::invalidate);
        return this;
    }

    @Override public Canvas tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Canvas dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Canvas acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public CanvasPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
