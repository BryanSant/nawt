package io.github.swat.spi;

import java.util.List;

public record DropDownConfig(List<String> items, int initialSelection) {
    public DropDownConfig {
        if (items == null) items = List.of();
        else items = List.copyOf(items);
        if (initialSelection < -1 || initialSelection >= items.size()) initialSelection = -1;
    }
}
