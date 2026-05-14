package cc.nawt.backend.macos;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

/**
 * MapKit framework binding helpers. Force-loads the framework on class init so
 * {@code Objc.cls("MKMapView")} and friends resolve; exposes the struct
 * layouts AppKit uses for coordinates and regions.
 *
 * <p>Layouts:
 * <pre>
 * CLLocationCoordinate2D { double latitude; double longitude; }
 * MKCoordinateSpan       { double latitudeDelta; double longitudeDelta; }
 * MKCoordinateRegion     { CLLocationCoordinate2D center; MKCoordinateSpan span; }
 * </pre>
 */
final class MapKit {
    private MapKit() {}

    static {
        // Force-load MapKit so MKMapView / MKPointAnnotation / etc. register
        // with the Objective-C runtime.
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/MapKit.framework/MapKit", Objc.GLOBAL);
    }

    static final MemoryLayout CL_COORDINATE_2D = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("latitude"),
        ValueLayout.JAVA_DOUBLE.withName("longitude"));

    static final MemoryLayout MK_COORDINATE_SPAN = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("latitudeDelta"),
        ValueLayout.JAVA_DOUBLE.withName("longitudeDelta"));

    static final MemoryLayout MK_COORDINATE_REGION = MemoryLayout.structLayout(
        CL_COORDINATE_2D.withName("center"),
        MK_COORDINATE_SPAN.withName("span"));

    /**
     * Translate a 1.0–20.0 zoom level (web-map convention — 1 is "show the
     * world", 20 is "street level") into MKCoordinateRegion span deltas.
     *
     * <p>Formula: {@code span = 360 / 2^zoom} degrees. At zoom 13 this gives
     * ≈ 0.044°, roughly a few city blocks at mid-latitudes — the same default
     * SwiftUI's {@code MKMapView(initialPosition:)} lands on for a
     * single-coordinate region.
     */
    static double zoomToSpanDegrees(double zoom) {
        return 360.0 / Math.pow(2.0, zoom);
    }

    /** Force class-init from outside. */
    static void ensureLoaded() {}
}
