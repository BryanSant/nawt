package cc.nawt;

import cc.nawt.spi.Orientation;
import cc.nawt.spi.SplitterConfig;
import cc.nawt.spi.SplitterPeer;

/** Two-child resizable splitter. */
public final class Splitter implements Container {

    private final SplitterPeer peer;
    private final Widget start;
    private final Widget end;

    private Splitter(SplitterPeer peer, Widget start, Widget end) {
        this.peer = peer;
        this.start = start;
        this.end = end;
    }

    public static Splitter horizontal(Widget start, Widget end) {
        return builder().orientation(Orientation.HORIZONTAL).start(start).end(end).build();
    }

    public static Splitter vertical(Widget start, Widget end) {
        return builder().orientation(Orientation.VERTICAL).start(start).end(end).build();
    }

    public static Builder builder() { return new Builder(); }

    @Override public Splitter tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Splitter dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Splitter acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public SplitterPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            if (start != null) start.close();
            if (end != null) end.close();
            peer.close();
        });
    }

    public static final class Builder {
        private Orientation orientation = Orientation.HORIZONTAL;
        private int position = 0;
        private Widget start;
        private Widget end;

        private Builder() {}

        public Builder orientation(Orientation o) { this.orientation = o; return this; }
        public Builder position(int px) { this.position = px; return this; }
        public Builder start(Widget w) { this.start = w; return this; }
        public Builder end(Widget w) { this.end = w; return this; }

        public Splitter build() {
            return Ui.onUi(() -> {
                SplitterPeer p = Toolkit.requireLaunched().peerFactory()
                    .createSplitter(new SplitterConfig(orientation, position));
                if (start != null) p.setStart(start.peer());
                if (end != null) p.setEnd(end.peer());
                return new Splitter(p, start, end);
            });
        }
    }
}
