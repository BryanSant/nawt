package io.github.swat;

/** Minimal cross-platform 2D drawing surface passed into a {@link Canvas}'s paint callback. */
public interface Painter {
    /** Set the active color in linear sRGB; channels in [0, 1]. */
    Painter color(double r, double g, double b, double a);
    default Painter color(double r, double g, double b) { return color(r, g, b, 1.0); }

    /** Fill a rectangle in the current color. */
    Painter fillRect(double x, double y, double w, double h);

    /** Stroke a rectangle outline in the current color. */
    Painter strokeRect(double x, double y, double w, double h);

    /** Stroke a single line segment in the current color. */
    Painter line(double x1, double y1, double x2, double y2);
}
