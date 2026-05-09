package io.github.swat.spi;

public record ContainerConfig(Orientation orientation, int spacing, int padding) {
    public ContainerConfig {
        if (orientation == null) orientation = Orientation.VERTICAL;
        if (spacing < 0) spacing = 0;
        if (padding < 0) padding = 0;
    }
}
