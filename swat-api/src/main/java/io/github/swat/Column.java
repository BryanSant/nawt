package io.github.swat;

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
        private final java.util.List<Widget> kids = new java.util.ArrayList<>();

        private Builder() {}

        public Builder spacing(int px) { this.spacing = px; return this; }
        public Builder padding(int px) { this.padding = px; return this; }
        public Builder add(Widget child) { kids.add(child); return this; }
        public Builder children(Widget... ws) {
            for (Widget w : ws) kids.add(w);
            return this;
        }

        public Column build() {
            List<Widget> snapshot = List.copyOf(kids);
            return Ui.onUi(() -> {
                ContainerPeer p = Toolkit.requireLaunched().peerFactory()
                    .createContainer(new ContainerConfig(Orientation.VERTICAL, spacing, padding));
                for (Widget w : snapshot) p.append(w.peer());
                return new Column(p, snapshot);
            });
        }
    }
}
