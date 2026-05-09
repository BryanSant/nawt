package io.github.swat;

import io.github.swat.spi.HeaderBarConfig;
import io.github.swat.spi.HeaderBarPeer;
import io.github.swat.spi.Peer;

import java.util.ArrayList;
import java.util.List;

/**
 * Window chrome — a top-of-window bar with start (leading) and end (trailing)
 * item slots. Title comes from the host {@link Window}, not from the bar.
 *
 * <p>Backends: {@code NSToolbar} in unified style on macOS;
 * {@code AdwHeaderBar} inside {@code AdwToolbarView} inside {@code AdwWindow}
 * on Linux. Items are typically {@link Button}s but any widget works.
 *
 * <p>HeaderBar is window chrome, not a layout widget — it does not implement
 * {@link Widget}, cannot be added to a {@code Container}, and is attached only
 * via {@link Window.Builder#headerBar(HeaderBar)}.
 */
public final class HeaderBar implements AutoCloseable {

    private final HeaderBarPeer peer;
    private final List<Widget> startItems;
    private final List<Widget> endItems;

    private HeaderBar(HeaderBarPeer peer, List<Widget> startItems, List<Widget> endItems) {
        this.peer = peer;
        this.startItems = startItems;
        this.endItems = endItems;
    }

    public static Builder builder() { return new Builder(); }

    public HeaderBarPeer peer() { return peer; }

    public List<Widget> startItems() { return startItems; }
    public List<Widget> endItems() { return endItems; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (Widget w : startItems) w.close();
            for (Widget w : endItems) w.close();
            peer.close();
        });
    }

    public static final class Builder {
        private final List<Widget> start = new ArrayList<>();
        private final List<Widget> end = new ArrayList<>();

        private Builder() {}

        /** Add a widget to the start (leading) region. Order matters. */
        public Builder start(Widget w) { if (w != null) start.add(w); return this; }

        /** Add a widget to the end (trailing) region. Order matters. */
        public Builder end(Widget w) { if (w != null) end.add(w); return this; }

        public HeaderBar build() {
            List<Widget> startSnap = List.copyOf(start);
            List<Widget> endSnap = List.copyOf(end);
            return Ui.onUi(() -> {
                List<Peer> startPeers = new ArrayList<>(startSnap.size());
                for (Widget w : startSnap) startPeers.add(w.peer());
                List<Peer> endPeers = new ArrayList<>(endSnap.size());
                for (Widget w : endSnap) endPeers.add(w.peer());
                HeaderBarPeer p = Toolkit.requireLaunched().peerFactory()
                    .createHeaderBar(new HeaderBarConfig(startPeers, endPeers));
                return new HeaderBar(p, startSnap, endSnap);
            });
        }
    }
}
