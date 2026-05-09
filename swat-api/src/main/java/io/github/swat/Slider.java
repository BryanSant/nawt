package io.github.swat;

import io.github.swat.event.ValueChangeEvent;
import io.github.swat.spi.Orientation;
import io.github.swat.spi.SliderConfig;
import io.github.swat.spi.SliderPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Slider implements Widget {

    private final SliderPeer peer;
    private final List<Consumer<ValueChangeEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ValueChangeEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Slider(SliderPeer peer) {
        this.peer = peer;
        peer.onValueChange(this::dispatch);
    }

    public static Slider of() { return of(0, 1, 0); }

    public static Slider of(double min, double max, double initial) {
        return Ui.onUi(() -> {
            SliderPeer p = Toolkit.requireLaunched().peerFactory()
                .createSlider(new SliderConfig(min, max, initial, Orientation.HORIZONTAL));
            return new Slider(p);
        });
    }

    public static Slider vertical(double min, double max, double initial) {
        return Ui.onUi(() -> {
            SliderPeer p = Toolkit.requireLaunched().peerFactory()
                .createSlider(new SliderConfig(min, max, initial, Orientation.VERTICAL));
            return new Slider(p);
        });
    }

    public Slider range(double min, double max) {
        Ui.runOnUi(() -> peer.setRange(min, max));
        return this;
    }

    public Slider value(double value) {
        Ui.runOnUi(() -> peer.setValue(value));
        return this;
    }

    public double value() {
        return Ui.onUi(peer::getValue);
    }

    public Slider onValueChange(Consumer<ValueChangeEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public Slider onValueChangeSync(Consumer<ValueChangeEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(double value) {
        ValueChangeEvent e = new ValueChangeEvent(this, value);
        for (Consumer<ValueChangeEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<ValueChangeEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public Slider tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Slider dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Slider acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public SliderPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
