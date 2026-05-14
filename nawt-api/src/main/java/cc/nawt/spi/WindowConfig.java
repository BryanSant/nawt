package cc.nawt.spi;

public record WindowConfig(String title, int width, int height, boolean resizable, boolean fitContent) {
    public WindowConfig {
        if (title == null) title = "";
        if (width <= 0) width = 640;
        if (height <= 0) height = 480;
    }

    public WindowConfig(String title, int width, int height) {
        this(title, width, height, true, false);
    }

    public WindowConfig(String title, int width, int height, boolean resizable) {
        this(title, width, height, resizable, false);
    }
}
