package cc.nawt.spi;

import cc.nawt.spi.Alignment;

/**
 * @param background the base widget; fills the overlay's bounds
 * @param foreground the widget painted on top; positioned per {@code alignment}
 *                   at its intrinsic size (no stretch)
 * @param alignment  positional alignment of the foreground within the overlay
 */
public record OverlayConfig(Peer background, Peer foreground, Alignment alignment) {
    public OverlayConfig {
        if (background == null) throw new IllegalArgumentException("background must not be null");
        if (foreground == null) throw new IllegalArgumentException("foreground must not be null");
        if (alignment == null) alignment = Alignment.CENTER;
    }
}
