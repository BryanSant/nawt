package cc.nawt.spi;

public record MenuActionConfig(String text, String shortcut, boolean enabled) {
    public MenuActionConfig {
        if (text == null) text = "";
        if (shortcut != null && shortcut.isBlank()) shortcut = null;
    }
}
