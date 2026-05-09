package io.github.swat.spi;

import io.github.swat.Painter;

import java.util.function.Consumer;

public non-sealed interface CanvasPeer extends Peer {
    /** Register a paint callback. The Painter is only valid for the duration of the call. */
    void onPaint(Consumer<Painter> trigger);
    /** Mark the canvas as needing a redraw. */
    void invalidate();
}
