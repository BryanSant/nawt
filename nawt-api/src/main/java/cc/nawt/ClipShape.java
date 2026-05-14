package cc.nawt;

/**
 * A mask shape applied to an {@link Image} (or any widget that adopts the
 * clip-shape modifier in future). Sealed so backends can enumerate cases
 * with a {@code switch} pattern without a default branch.
 *
 * <p>Use the singleton {@link #CIRCLE} for a fully circular crop, or
 * {@link #roundedRect(int)} for a rounded-rectangle crop with the given
 * corner radius in points.
 */
public sealed interface ClipShape {

    /** Singleton circular clip — pins corners to half the view's smaller dimension. */
    Circle CIRCLE = new Circle();

    /** Rounded rectangle clip with the given corner radius. */
    static RoundedRect roundedRect(int cornerRadius) {
        if (cornerRadius < 0) {
            throw new IllegalArgumentException("cornerRadius must be >= 0: " + cornerRadius);
        }
        return new RoundedRect(cornerRadius);
    }

    final class Circle implements ClipShape {
        private Circle() {}
    }

    record RoundedRect(int cornerRadius) implements ClipShape {}
}
