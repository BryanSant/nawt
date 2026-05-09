package io.github.swat.spi;

public record TextFieldConfig(String initialText) {
    public TextFieldConfig {
        if (initialText == null) initialText = "";
    }
}
