package io.github.swat.spi;

public record FrameConfig(String title) {
    public FrameConfig {
        if (title == null) title = "";
    }
}
