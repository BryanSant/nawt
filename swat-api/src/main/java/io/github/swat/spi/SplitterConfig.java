package io.github.swat.spi;

/** {@code position} is the initial divider position in pixels (0 = use natural sizing). */
public record SplitterConfig(Orientation orientation, int position) {
    public SplitterConfig {
        if (orientation == null) orientation = Orientation.HORIZONTAL;
        if (position < 0) position = 0;
    }
}
