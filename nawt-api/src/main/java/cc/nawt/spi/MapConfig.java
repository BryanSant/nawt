package cc.nawt.spi;

import cc.nawt.Coordinate;

import java.util.List;

/**
 * @param center      initial centre of the visible region
 * @param zoom        1.0 (world) to 20.0 (street). The backend translates this
 *                    to whatever native region/span representation it uses.
 * @param pins        initial pins to display; may be empty
 * @param interactive whether the user can pan/zoom the map
 */
public record MapConfig(Coordinate center, double zoom, List<Pin> pins, boolean interactive) {

    public MapConfig {
        if (center == null) throw new IllegalArgumentException("center must not be null");
        if (zoom < 1.0 || zoom > 20.0) {
            throw new IllegalArgumentException("zoom must be in [1.0, 20.0]: " + zoom);
        }
        pins = pins == null ? List.of() : List.copyOf(pins);
    }

    public MapConfig(Coordinate center, double zoom) {
        this(center, zoom, List.of(), true);
    }

    /** A single pin placement. {@code title} may be null. */
    public record Pin(Coordinate at, String title) {
        public Pin {
            if (at == null) throw new IllegalArgumentException("at must not be null");
        }
    }
}
