package io.github.swat;

import io.github.swat.spi.Alignment;
import io.github.swat.spi.ChildLayoutConfig;
import io.github.swat.spi.ContainerConfig;
import io.github.swat.spi.ContainerPeer;
import io.github.swat.spi.Orientation;

import java.util.List;

public final class Column implements Container {

    private final ContainerPeer peer;
    private final List<Widget> children;

    private Column(ContainerPeer peer, List<Widget> children) {
        this.peer = peer;
        this.children = children;
    }

    public static Column of(Widget... children) {
        return builder().children(children).build();
    }

    public static Builder builder() { return new Builder(); }

    public List<Widget> children() { return children; }

    @Override public Column tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Column dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Column acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

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

        public Column build() {
            List<Widget> snapshot = List.copyOf(kids);
            List<ChildLayoutConfig> hintSnapshot = List.copyOf(hints);
            return Ui.onUi(() -> {
                ContainerPeer p = Toolkit.requireLaunched().peerFactory()
                    .createContainer(new ContainerConfig(Orientation.VERTICAL, spacing, padding, crossAxis));
                for (int i = 0; i < snapshot.size(); i++) {
                    p.append(snapshot.get(i).peer(), hintSnapshot.get(i));
                }
                return new Column(p, snapshot);
            });
        }
    }
}
