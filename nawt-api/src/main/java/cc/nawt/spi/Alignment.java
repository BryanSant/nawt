package cc.nawt.spi;

/**
 * Cross-axis alignment for a child within a Row or Column, and main-axis
 * alignment when expressed at the container level.
 *
 * <p>Values map cleanly to native primitives on every NAWT backend:
 * AppKit's {@code NSStackView} alignment / {@code NSLayoutAttribute},
 * GTK's {@code GtkAlign}, and WinUI's {@code HorizontalAlignment} /
 * {@code VerticalAlignment}. {@link #BASELINE} is meaningful only for the
 * cross axis of a horizontal Row containing text-bearing widgets — backends
 * approximate it with {@link #CENTER} otherwise. See {@code LAYOUT.md}.
 */
public enum Alignment {
    START,
    CENTER,
    END,
    STRETCH,
    BASELINE
}
