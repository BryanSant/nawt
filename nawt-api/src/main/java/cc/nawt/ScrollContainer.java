package cc.nawt;

import cc.nawt.spi.ScrollContainerConfig;
import cc.nawt.spi.ScrollContainerPeer;

/** Single-child scrollable wrapper. */
public final class ScrollContainer implements Container {

    private final ScrollContainerPeer peer;
    private final Widget content;

    private ScrollContainer(ScrollContainerPeer peer, Widget content) {
        this.peer = peer;
        this.content = content;
    }

    public static ScrollContainer of(Widget content) {
        return builder().content(content).build();
    }

    public static Builder builder() { return new Builder(); }

    public Widget content() { return content; }

    @Override public ScrollContainer tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public ScrollContainer dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public ScrollContainer acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public ScrollContainerPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            if (content != null) content.close();
            peer.close();
        });
    }

    public static final class Builder {
        private boolean horizontal = true;
        private boolean vertical = true;
        private Widget content;

        private Builder() {}

        public Builder horizontal(boolean on) { this.horizontal = on; return this; }
        public Builder vertical(boolean on) { this.vertical = on; return this; }
        public Builder content(Widget content) { this.content = content; return this; }

        public ScrollContainer build() {
            return Ui.onUi(() -> {
                ScrollContainerPeer p = Toolkit.requireLaunched().peerFactory()
                    .createScrollContainer(new ScrollContainerConfig(horizontal, vertical));
                if (content != null) p.setContent(content.peer());
                return new ScrollContainer(p, content);
            });
        }
    }
}
