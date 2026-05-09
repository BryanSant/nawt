package io.github.swat.spi;

public record ButtonConfig(String text) {
    public ButtonConfig {
        if (text == null) text = "";
    }
}
