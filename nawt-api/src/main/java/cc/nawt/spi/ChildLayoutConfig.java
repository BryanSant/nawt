package cc.nawt.spi;

/**
 * Per-child layout hints for a {@link ContainerPeer}.
 *
 * <p>Hints are advisory: each backend translates them into the closest native
 * primitive (AppKit content-hugging priority, GTK {@code hexpand}/{@code halign},
 * WinUI {@code Grid.ColumnDefinition} star sizing). When a backend cannot
 * express a hint exactly, it must document the deviation in {@code LAYOUT.md}.
 *
 * @param expand    if {@code true}, this child absorbs slack along the
 *                  container's main axis. Multiple expanding children share
 *                  slack equally.
 * @param alignSelf cross-axis alignment override for this child. {@code null}
 *                  means inherit the container's cross-axis default.
 */
public record ChildLayoutConfig(boolean expand, Alignment alignSelf) {

    public static final ChildLayoutConfig DEFAULT = new ChildLayoutConfig(false, null);
    public static final ChildLayoutConfig EXPAND = new ChildLayoutConfig(true, null);

    public static ChildLayoutConfig expanding() {
        return EXPAND;
    }

    public static ChildLayoutConfig aligned(Alignment alignment) {
        return new ChildLayoutConfig(false, alignment);
    }

    public static ChildLayoutConfig expanding(Alignment alignment) {
        return new ChildLayoutConfig(true, alignment);
    }
}
