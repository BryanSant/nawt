package io.github.swat.spi;

public record CanvasConfig(int width, int height) {
    public CanvasConfig {
        if (width < 0) width = 0;
        if (height < 0) height = 0;
    }
}
