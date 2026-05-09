package io.github.swat.spi;

public record LabelConfig(String text) {
    public LabelConfig {
        if (text == null) text = "";
    }
}
