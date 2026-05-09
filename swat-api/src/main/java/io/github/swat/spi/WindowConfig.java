package io.github.swat.spi;

public record WindowConfig(String title, int width, int height) {
    public WindowConfig {
        if (title == null) title = "";
        if (width <= 0) width = 640;
        if (height <= 0) height = 480;
    }
}
