package cc.nawt;

/**
 * A geographic point in WGS-84. Used by {@link Map} and any future API that
 * pins to a location on the globe.
 *
 * @param latitude  degrees north of the equator; range −90.0 to +90.0
 * @param longitude degrees east of the prime meridian; range −180.0 to +180.0
 */
public record Coordinate(double latitude, double longitude) {
    public Coordinate {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
    }

    public static Coordinate of(double latitude, double longitude) {
        return new Coordinate(latitude, longitude);
    }
}
