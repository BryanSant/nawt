package io.github.swat;

import io.github.swat.spi.FrameConfig;
import io.github.swat.spi.FramePeer;

/** Titled bordered container with a single child. */
public final class Frame implements Container {

    private final FramePeer peer;
    private final Widget content;

    private Frame(FramePeer peer, Widget content) {
        this.peer = peer;
        this.content = content;
    }

    public static Builder builder() { return new Builder(); }

    public static Frame of(String title, Widget content) {
        return builder().title(title).content(content).build();
    }

    public Widget content() { return content; }

    public Frame title(String title) {
        Ui.runOnUi(() -> peer.setTitle(title));
        return this;
    }

    @Override public Frame tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Frame dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Frame acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public FramePeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            if (content != null) content.close();
            peer.close();
        });
    }

    public static final class Builder {
        private String title = "";
        private Widget content;

        private Builder() {}

        public Builder title(String title) { this.title = title; return this; }
        public Builder content(Widget content) { this.content = content; return this; }

        public Frame build() {
            return Ui.onUi(() -> {
                FramePeer p = Toolkit.requireLaunched().peerFactory()
                    .createFrame(new FrameConfig(title));
                if (content != null) p.setContent(content.peer());
                return new Frame(p, content);
            });
        }
    }
}
