package io.github.swat;

import io.github.swat.event.ToggleEvent;
import io.github.swat.spi.SwitchConfig;
import io.github.swat.spi.SwitchPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Switch implements Widget {

    private final SwitchPeer peer;
    private final List<Consumer<ToggleEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ToggleEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Switch(SwitchPeer peer) {
        this.peer = peer;
        peer.onToggle(this::dispatch);
    }

    public static Switch of() { return of(false); }

    public static Switch of(boolean on) {
        return Ui.onUi(() -> {
            SwitchPeer p = Toolkit.requireLaunched().peerFactory()
                .createSwitch(new SwitchConfig(on));
            return new Switch(p);
        });
    }

    public Switch on(boolean on) {
        Ui.runOnUi(() -> peer.setOn(on));
        return this;
    }

    public boolean isOn() {
        return Ui.onUi(peer::isOn);
    }

    public Switch onToggle(Consumer<ToggleEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public Switch onToggleSync(Consumer<ToggleEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(boolean on) {
        ToggleEvent e = new ToggleEvent(this, on);
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

    @Override public Switch tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Switch dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Switch acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public SwitchPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
