package cc.nawt.spi;

/**
 * Container-level layout configuration for Row and Column.
 *
 * @param orientation main-axis direction
 * @param spacing     pixels between adjacent children
 * @param padding     pixels of inset around the container's content rectangle
 * @param crossAxis   default cross-axis alignment for children that do not
 *                    override it via {@link ChildLayoutConfig#alignSelf()}.
 *                    Defaults to {@link Alignment#STRETCH}.
 */
public record ContainerConfig(Orientation orientation, int spacing, int padding, Alignment crossAxis) {
    public ContainerConfig {
        if (orientation == null) orientation = Orientation.VERTICAL;
        if (spacing < 0) spacing = 0;
        if (padding < 0) padding = 0;
        if (crossAxis == null) crossAxis = Alignment.STRETCH;
    }

    public ContainerConfig(Orientation orientation, int spacing, int padding) {
        this(orientation, spacing, padding, Alignment.STRETCH);
    }
}
