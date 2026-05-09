package io.github.swat.spi;

import java.util.function.Consumer;

public non-sealed interface TreePeer extends Peer {
    /** Replace the entire tree. */
    void setRoot(TreeNodeData root);
    /** Currently-selected path, or null if no selection. Path is the index chain from the root. */
    int[] selectedPath();
    /** Programmatically select a node by path; pass null or empty to clear. */
    void selectPath(int[] path);
    /** Trigger fires with the new path, or null when selection clears. */
    void onSelectionChange(Consumer<int[]> trigger);
}
