package cc.nawt;

import cc.nawt.spi.Alignment;
import cc.nawt.spi.ChildLayoutConfig;
import cc.nawt.spi.ContainerConfig;
import cc.nawt.spi.ContainerPeer;
import cc.nawt.spi.Orientation;

import java.util.List;

public final class Row implements Container {

    private final ContainerPeer peer;
    private final List<Widget> children;

    private Row(ContainerPeer peer, List<Widget> children) {
        this.peer = peer;
        this.children = children;
    }

    public static Row of(Widget... children) {
        return builder().children(children).build();
    }

    public static Builder builder() { return new Builder(); }

    public List<Widget> children() { return children; }

    @Override public Row tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Row dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Row acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public ContainerPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (Widget child : children) child.close();
            peer.close();
        });
    }

    public static final class Builder {
        private int spacing = 8;
        private int padding = 0;
        private Alignment crossAxis = Alignment.STRETCH;
        private final java.util.List<Widget> kids = new java.util.ArrayList<>();
        private final java.util.List<ChildLayoutConfig> hints = new java.util.ArrayList<>();

        private Builder() {}

        public Builder spacing(int px) { this.spacing = px; return this; }
        public Builder padding(int px) { this.padding = px; return this; }
        public Builder alignCross(Alignment a) { this.crossAxis = a; return this; }

        public Builder add(Widget child) {
            kids.add(child); hints.add(ChildLayoutConfig.DEFAULT); return this;
        }
        public Builder add(Widget child, ChildLayoutConfig childHints) {
            kids.add(child); hints.add(childHints == null ? ChildLayoutConfig.DEFAULT : childHints); return this;
        }
        public Builder expand(Widget child) {
            return add(child, ChildLayoutConfig.EXPAND);
        }
        public Builder children(Widget... ws) {
            for (Widget w : ws) add(w);
            return this;
        }

        public Row build() {
            List<Widget> snapshot = List.copyOf(kids);
            List<ChildLayoutConfig> hintSnapshot = List.copyOf(hints);
            return Ui.onUi(() -> {
                ContainerPeer p = Toolkit.requireLaunched().peerFactory()
                    .createContainer(new ContainerConfig(Orientation.HORIZONTAL, spacing, padding, crossAxis));
                for (int i = 0; i < snapshot.size(); i++) {
                    p.append(snapshot.get(i).peer(), hintSnapshot.get(i));
                }
                return new Row(p, snapshot);
            });
        }
    }
}
