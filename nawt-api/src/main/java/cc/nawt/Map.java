package cc.nawt;

import cc.nawt.spi.MapConfig;
import cc.nawt.spi.MapPeer;

import java.util.ArrayList;
import java.util.List;

/**
 * A native map view pinned at a coordinate. Backed by {@code MKMapView} on
 * macOS, {@code libshumate} on GTK (planned), and {@code MapControl} on
 * WinUI 3 (planned). Pixel-perfect output differs per platform by design.
 *
 * <p>Requires {@link Capability#MAP} on the active backend.
 */
public final class Map implements Widget {

    private final MapPeer peer;

    private Map(MapPeer peer) { this.peer = peer; }

    /** Centre the map on the given coordinate at a default zoom of 13 (city-block scale). */
    public static Map of(Coordinate center) { return builder().center(center).build(); }

    public static Builder builder() { return new Builder(); }

    public Map center(Coordinate c) {
        Ui.runOnUi(() -> peer.setCenter(c));
        return this;
    }

    /** Set the map's zoom level. {@code 1.0} = world; {@code 20.0} = street-level. */
    public Map zoom(double level) {
        Ui.runOnUi(() -> peer.setZoom(level));
        return this;
    }

    public Map pin(Coordinate at, String title) {
        Ui.runOnUi(() -> peer.addPin(at, title));
        return this;
    }

    public Map clearPins() {
        Ui.runOnUi(peer::clearPins);
        return this;
    }

    /** Enable or disable user pan/zoom interaction. Default {@code true}. */
    public Map interactive(boolean enabled) {
        Ui.runOnUi(() -> peer.setInteractive(enabled));
        return this;
    }

    @Override public Map tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Map dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Map acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public MapPeer peer() { return peer; }

    @Override public void close() { Ui.runOnUi(peer::close); }

    public static final class Builder {
        private Coordinate center;
        private double zoom = 13.0;
        private boolean interactive = true;
        private final List<MapConfig.Pin> pins = new ArrayList<>();

        private Builder() {}

        public Builder center(Coordinate c) { this.center = c; return this; }
        public Builder zoom(double level) { this.zoom = level; return this; }
        public Builder interactive(boolean enabled) { this.interactive = enabled; return this; }
        public Builder pin(Coordinate at, String title) { pins.add(new MapConfig.Pin(at, title)); return this; }

        public Map build() {
            if (center == null) throw new IllegalStateException("center coordinate is required");
            MapConfig cfg = new MapConfig(center, zoom, List.copyOf(pins), interactive);
            return Ui.onUi(() -> {
                MapPeer p = Toolkit.requireLaunched().peerFactory().createMap(cfg);
                return new Map(p);
            });
        }
    }
}
