package cc.nawt;

import cc.nawt.spi.Alignment;
import cc.nawt.spi.OverlayConfig;
import cc.nawt.spi.OverlayPeer;

/**
 * Z-stack of two widgets: a {@code background} that fills the overlay's
 * bounds, and a {@code foreground} painted on top at its intrinsic size,
 * positioned by {@link Alignment}. Use for badges, status pips, or — in the
 * Landmarks sample — a circular profile photo centred over a map.
 */
public final class Overlay implements Container {

    private final OverlayPeer peer;
    private final Widget background;
    private final Widget foreground;

    private Overlay(OverlayPeer peer, Widget background, Widget foreground) {
        this.peer = peer;
        this.background = background;
        this.foreground = foreground;
    }

    /** Default {@code CENTER} alignment of {@code foreground} over {@code background}. */
    public static Overlay of(Widget background, Widget foreground) {
        return of(background, foreground, Alignment.CENTER);
    }

    public static Overlay of(Widget background, Widget foreground, Alignment alignment) {
        if (background == null) throw new IllegalArgumentException("background must not be null");
        if (foreground == null) throw new IllegalArgumentException("foreground must not be null");
        Alignment a = alignment == null ? Alignment.CENTER : alignment;
        return Ui.onUi(() -> {
            OverlayPeer p = Toolkit.requireLaunched().peerFactory()
                .createOverlay(new OverlayConfig(background.peer(), foreground.peer(), a));
            return new Overlay(p, background, foreground);
        });
    }

    public Widget background() { return background; }
    public Widget foreground() { return foreground; }

    @Override public Overlay tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Overlay dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Overlay acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public OverlayPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            background.close();
            foreground.close();
            peer.close();
        });
    }
}
