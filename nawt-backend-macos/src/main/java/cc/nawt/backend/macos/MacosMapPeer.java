package cc.nawt.backend.macos;

import cc.nawt.Coordinate;
import cc.nawt.spi.MapConfig;
import cc.nawt.spi.MapPeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Interactive MKMapView. Region is computed from a (centre, zoom) pair via
 * {@link MapKit#zoomToSpanDegrees(double)}; pins are {@code MKPointAnnotation}.
 */
final class MacosMapPeer implements MapPeer {

    private static final MemoryLayout NSRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

    private final MemorySegment view; // MKMapView, retained
    private Coordinate center;
    private double zoom;

    MacosMapPeer(MapConfig cfg) {
        MapKit.ensureLoaded();
        this.center = cfg.center();
        this.zoom = cfg.zoom();

        MemorySegment alloc = Objc.send_alloc(Objc.cls("MKMapView"));
        MemorySegment v;
        try (var arena = Arena.ofConfined()) {
            MemorySegment frame = arena.allocate(NSRECT);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, 400.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, 300.0);
            try {
                v = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, NSRECT))
                    .invoke(alloc, Objc.sel("initWithFrame:"), frame);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
        this.view = Objc.sendPtr(v, Objc.sel("retain"));

        Objc.sendVoidBool(view, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        applyRegion(center, zoom);
        setInteractive(cfg.interactive());

        for (MapConfig.Pin pin : cfg.pins()) {
            addPin(pin.at(), pin.title());
        }
    }

    /** Used by {@link MacosContainerPeer#peerView}. */
    MemorySegment view() { return view; }

    private void applyRegion(Coordinate centre, double z) {
        double span = MapKit.zoomToSpanDegrees(z);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment region = arena.allocate(MapKit.MK_COORDINATE_REGION);
            // center.latitude, center.longitude, span.latitudeDelta, span.longitudeDelta
            region.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, centre.latitude());
            region.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, centre.longitude());
            region.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, span);
            region.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, span);
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(
                        Objc.PTR, Objc.PTR, MapKit.MK_COORDINATE_REGION, Objc.BOOL))
                    .invoke(view, Objc.sel("setRegion:animated:"), region, false);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
    }

    @Override public void setCenter(Coordinate centre) {
        if (centre == null) return;
        this.center = centre;
        applyRegion(centre, zoom);
    }

    @Override public void setZoom(double z) {
        if (z < 1.0) z = 1.0;
        if (z > 20.0) z = 20.0;
        this.zoom = z;
        applyRegion(center, z);
    }

    @Override public void addPin(Coordinate at, String title) {
        if (at == null) return;
        MemorySegment annotation = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("MKPointAnnotation")), Objc.sel("init"));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment coord = arena.allocate(MapKit.CL_COORDINATE_2D);
            coord.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, at.latitude());
            coord.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, at.longitude());
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(
                        Objc.PTR, Objc.PTR, MapKit.CL_COORDINATE_2D))
                    .invoke(annotation, Objc.sel("setCoordinate:"), coord);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
        if (title != null && !title.isEmpty()) {
            Objc.sendVoid(annotation, Objc.sel("setTitle:"), NSString.from(title));
        }
        Objc.sendVoid(view, Objc.sel("addAnnotation:"), annotation);
        // MKMapView retains; balance our +1 from alloc/init.
        Objc.sendVoid(annotation, Objc.sel("release"));
    }

    @Override public void clearPins() {
        MemorySegment annotations = Objc.sendPtr(view, Objc.sel("annotations"));
        if (annotations == null || annotations.address() == 0) return;
        Objc.sendVoid(view, Objc.sel("removeAnnotations:"), annotations);
    }

    @Override public void setInteractive(boolean enabled) {
        Objc.sendVoidBool(view, Objc.sel("setScrollEnabled:"), enabled);
        Objc.sendVoidBool(view, Objc.sel("setZoomEnabled:"), enabled);
        Objc.sendVoidBool(view, Objc.sel("setRotateEnabled:"), enabled);
        Objc.sendVoidBool(view, Objc.sel("setPitchEnabled:"), enabled);
    }

    @Override public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
