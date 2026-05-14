package cc.nawt.spi;

public record ExpanderConfig(String title, boolean initialExpanded) {
    public ExpanderConfig {
        if (title == null) title = "";
    }
}
