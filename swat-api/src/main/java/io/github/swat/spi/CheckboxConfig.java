package io.github.swat.spi;

public record CheckboxConfig(String text, boolean initialChecked) {
    public CheckboxConfig {
        if (text == null) text = "";
    }
}
