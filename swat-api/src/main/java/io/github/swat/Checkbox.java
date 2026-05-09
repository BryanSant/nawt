package io.github.swat;

import io.github.swat.event.ToggleEvent;
import io.github.swat.spi.CheckboxConfig;
import io.github.swat.spi.CheckboxPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Checkbox implements Widget {

    private final CheckboxPeer peer;
    private final List<Consumer<ToggleEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ToggleEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Checkbox(CheckboxPeer peer) {
        this.peer = peer;
        peer.onToggle(this::dispatch);
    }

    public static Checkbox of(String text) { return of(text, false); }

    public static Checkbox of(String text, boolean checked) {
        return Ui.onUi(() -> {
            CheckboxPeer p = Toolkit.requireLaunched().peerFactory()
                .createCheckbox(new CheckboxConfig(text, checked));
            return new Checkbox(p);
        });
    }

    public Checkbox text(String text) {
        Ui.runOnUi(() -> peer.setText(text));
        return this;
    }

    public Checkbox checked(boolean checked) {
        Ui.runOnUi(() -> peer.setChecked(checked));
        return this;
    }

    public boolean isChecked() {
        return Ui.onUi(peer::isChecked);
    }

    public Checkbox onToggle(Consumer<ToggleEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public Checkbox onToggleSync(Consumer<ToggleEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(boolean checked) {
        ToggleEvent e = new ToggleEvent(this, checked);
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

    @Override public Checkbox tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Checkbox dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Checkbox acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public CheckboxPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
