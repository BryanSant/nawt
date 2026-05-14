package cc.nawt;

/**
 * Hint for a {@link Label}'s text colour. Maps to the host's semantic colour
 * roles — {@code NSColor.labelColor} / {@code .secondaryLabelColor} on macOS,
 * the GTK theme's primary/secondary CSS text colours on Linux — rather than
 * fixed RGB values, so light/dark mode and accent colour follow automatically.
 */
public enum LabelStyle {
    /** Default body text colour. */
    PRIMARY,
    /** Subdued secondary text (subtitles, captions, "By…" lines). */
    SECONDARY,
}
