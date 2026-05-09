package io.github.swat.spi;

public record RadioConfig(String text, boolean initialSelected) {
    public RadioConfig {
        if (text == null) text = "";
    }
}
