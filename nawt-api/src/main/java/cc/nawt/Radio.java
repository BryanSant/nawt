package cc.nawt;

import cc.nawt.event.ToggleEvent;
import cc.nawt.spi.RadioConfig;
import cc.nawt.spi.RadioPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Radio implements Widget {

    private final RadioPeer peer;
    private final List<Consumer<ToggleEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ToggleEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Radio(RadioPeer peer) {
        this.peer = peer;
        peer.onToggle(this::dispatch);
    }

    public static Radio of(String text) { return of(text, false); }

    public static Radio of(String text, boolean selected) {
        return Ui.onUi(() -> {
            RadioPeer p = Toolkit.requireLaunched().peerFactory()
                .createRadio(new RadioConfig(text, selected));
            return new Radio(p);
        });
    }

    public Radio text(String text) {
        Ui.runOnUi(() -> peer.setText(text));
        return this;
    }

    public Radio selected(boolean selected) {
        Ui.runOnUi(() -> peer.setSelected(selected));
        return this;
    }

    public boolean isSelected() {
        return Ui.onUi(peer::isSelected);
    }

    /** Place {@code others} into the same selection group as this radio. */
    public Radio group(Radio... others) {
        Ui.runOnUi(() -> {
            for (Radio r : others) peer.groupWith(r.peer);
        });
        return this;
    }

    public Radio onToggle(Consumer<ToggleEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public Radio onToggleSync(Consumer<ToggleEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(boolean selected) {
        ToggleEvent e = new ToggleEvent(this, selected);
        for (Consumer<ToggleEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<ToggleEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public Radio tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Radio dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Radio acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public RadioPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
