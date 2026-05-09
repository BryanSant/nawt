package io.github.swat.spi;

import java.util.List;

/** Immutable tree node. {@code children} may be empty but never null. */
public record TreeNodeData(String label, List<TreeNodeData> children) {
    public TreeNodeData {
        if (label == null) label = "";
        children = children == null ? List.of() : List.copyOf(children);
    }
}
