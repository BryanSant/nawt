package cc.nawt;

import cc.nawt.event.ClickEvent;
import cc.nawt.spi.ButtonConfig;
import cc.nawt.spi.ButtonPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Button implements Widget {

    private final ButtonPeer peer;
    private final List<Consumer<ClickEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ClickEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Button(ButtonPeer peer) {
        this.peer = peer;
        peer.onClick(this::dispatch);
    }

    public static Button of(String text) {
        return Ui.onUi(() -> {
            ButtonPeer p = Toolkit.requireLaunched().peerFactory()
                .createButton(new ButtonConfig(text));
            return new Button(p);
        });
    }

    public Button text(String text) {
        Ui.runOnUi(() -> peer.setText(text));
        return this;
    }

    /** Set the button title's font point size. {@code 0} restores the platform default. */
    public Button fontSize(int points) {
        Ui.runOnUi(() -> peer.setFontSize(points));
        return this;
    }

    /** Register a click handler that runs on a fresh virtual thread. */
    public Button onClick(Consumer<ClickEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    /**
     * Register a click handler that runs <em>synchronously on the UI thread</em>.
     * Use only for cheap, non-blocking work — slow handlers freeze the UI.
     */
    public Button onClickSync(Consumer<ClickEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch() {
        ClickEvent e = new ClickEvent(this);
        for (Consumer<ClickEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<ClickEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public Button tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Button dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Button acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public ButtonPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
