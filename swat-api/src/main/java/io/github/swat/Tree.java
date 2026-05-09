package io.github.swat;

import io.github.swat.event.TreeSelectionEvent;
import io.github.swat.spi.TreeConfig;
import io.github.swat.spi.TreeNodeData;
import io.github.swat.spi.TreePeer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Tree implements Widget {

    private final TreePeer peer;
    private volatile Node root;
    private final List<Consumer<TreeSelectionEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TreeSelectionEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Tree(TreePeer peer, Node root) {
        this.peer = peer;
        this.root = root;
        peer.onSelectionChange(this::dispatch);
    }

    public static Tree of(Node root) {
        return Ui.onUi(() -> {
            Node r = root == null ? new Node("") : root;
            TreePeer p = Toolkit.requireLaunched().peerFactory()
                .createTree(new TreeConfig(toData(r)));
            return new Tree(p, r);
        });
    }

    public Tree root(Node root) {
        Node r = root == null ? new Node("") : root;
        Ui.runOnUi(() -> {
            this.root = r;
            peer.setRoot(toData(r));
        });
        return this;
    }

    public int[] selectedPath() {
        return Ui.onUi(peer::selectedPath);
    }

    public Tree select(int... path) {
        Ui.runOnUi(() -> peer.selectPath(path));
        return this;
    }

    public Tree clearSelection() { return select(); }

    public Tree onSelectionChange(Consumer<TreeSelectionEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public Tree onSelectionChangeSync(Consumer<TreeSelectionEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(int[] path) {
        String label = path == null ? null : labelAt(root, path, 0);
        TreeSelectionEvent e = new TreeSelectionEvent(this, path == null ? null : path.clone(), label);
        for (Consumer<TreeSelectionEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<TreeSelectionEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    private static String labelAt(Node node, int[] path, int depth) {
        if (depth >= path.length) return node.label;
        int idx = path[depth];
        if (idx < 0 || idx >= node.children.size()) return null;
        return labelAt(node.children.get(idx), path, depth + 1);
    }

    private static TreeNodeData toData(Node n) {
        List<TreeNodeData> kids = new ArrayList<>(n.children.size());
        for (Node c : n.children) kids.add(toData(c));
        return new TreeNodeData(n.label, kids);
    }

    @Override public Tree tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Tree dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Tree acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public TreePeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }

    public static final class Node {
        public final String label;
        public final List<Node> children;

        public Node(String label, Node... children) {
            this.label = label == null ? "" : label;
            this.children = children == null ? List.of() : List.of(children);
        }

        public Node(String label, List<Node> children) {
            this.label = label == null ? "" : label;
            this.children = children == null ? List.of() : List.copyOf(children);
        }
    }
}
