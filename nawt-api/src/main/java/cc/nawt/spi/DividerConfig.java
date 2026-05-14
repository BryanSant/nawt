package cc.nawt.spi;

import cc.nawt.spi.Orientation;

public record DividerConfig(Orientation orientation) {
    public DividerConfig {
        if (orientation == null) orientation = Orientation.HORIZONTAL;
    }
}
