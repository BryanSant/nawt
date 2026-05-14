package cc.nawt.spi;

public record MenuConfig(String title) {
    public MenuConfig {
        if (title == null) title = "";
    }
}
