package cc.nawt.spi;

public record TextFieldConfig(String initialText) {
    public TextFieldConfig {
        if (initialText == null) initialText = "";
    }
}
