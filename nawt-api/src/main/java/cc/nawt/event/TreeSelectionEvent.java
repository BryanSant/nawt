package cc.nawt.event;

import cc.nawt.Tree;

/**
 * Fired when a {@link Tree}'s selection changes. {@code path} is null when
 * nothing is selected; otherwise it is the index chain from the root
 * (e.g. {@code [0, 2, 1]} = root's child 0 → child 2 → child 1).
 */
public record TreeSelectionEvent(Tree source, int[] path, String label) {}
