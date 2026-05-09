package io.github.swat;

import io.github.swat.event.ToggleEvent;
import io.github.swat.spi.ExpanderConfig;
import io.github.swat.spi.ExpanderPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Disclosure container — header + collapsible single child. */
public final class Expander implements Container {

    private final ExpanderPeer peer;
    private final Widget content;
    private final List<Consumer<ToggleEvent>> asyncListeners = new CopyOnWriteArrayList<>();

    private Expander(ExpanderPeer peer, Widget content) {
        this.peer = peer;
        this.content = content;
        peer.onExpandedChange(this::dispatch);
    }

    public static Builder builder() { return new Builder(); }

    public static Expander of(String title, Widget content) {
        return builder().title(title).content(content).build();
    }

    public Widget content() { return content; }

    public Expander title(String title) {
        Ui.runOnUi(() -> peer.setTitle(title));
        return this;
    }

    public Expander expanded(boolean on) {
        Ui.runOnUi(() -> peer.setExpanded(on));
        return this;
    }

    public boolean isExpanded() { return Ui.onUi(peer::isExpanded); }

    public Expander onExpandedChange(Consumer<ToggleEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    private void dispatch(boolean expanded) {
        ToggleEvent e = new ToggleEvent(this, expanded);
        for (Consumer<ToggleEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public Expander tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Expander dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Expander acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public ExpanderPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            if (content != null) content.close();
            peer.close();
        });
    }

    public static final class Builder {
        private String title = "";
        private boolean expanded;
        private Widget content;

        private Builder() {}

        public Builder title(String title) { this.title = title; return this; }
        public Builder expanded(boolean on) { this.expanded = on; return this; }
        public Builder content(Widget content) { this.content = content; return this; }

        public Expander build() {
            return Ui.onUi(() -> {
                ExpanderPeer p = Toolkit.requireLaunched().peerFactory()
                    .createExpander(new ExpanderConfig(title, expanded));
                if (content != null) p.setContent(content.peer());
                return new Expander(p, content);
            });
        }
    }
}
