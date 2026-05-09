package io.github.swat.spi;

import java.util.List;

public record ListViewConfig(List<String> items, int initialSelection, int visibleRowCount) {
    public ListViewConfig {
        items = items == null ? List.of() : List.copyOf(items);
        if (initialSelection < -1 || initialSelection >= items.size()) initialSelection = -1;
        if (visibleRowCount <= 0) visibleRowCount = 8;
    }
}
