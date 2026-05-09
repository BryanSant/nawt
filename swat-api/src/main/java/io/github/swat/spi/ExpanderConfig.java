package io.github.swat.spi;

public record ExpanderConfig(String title, boolean initialExpanded) {
    public ExpanderConfig {
        if (title == null) title = "";
    }
}
