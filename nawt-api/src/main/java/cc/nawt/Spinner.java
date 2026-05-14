package cc.nawt;

import cc.nawt.spi.SpinnerConfig;
import cc.nawt.spi.SpinnerPeer;

/** Indeterminate busy indicator (loading spinner). */
public final class Spinner implements Widget {

    private final SpinnerPeer peer;

    private Spinner(SpinnerPeer peer) { this.peer = peer; }

    public static Spinner of() { return of(false); }

    public static Spinner of(boolean active) {
        return Ui.onUi(() -> {
            SpinnerPeer p = Toolkit.requireLaunched().peerFactory()
                .createSpinner(new SpinnerConfig(active));
            return new Spinner(p);
        });
    }

    public Spinner active(boolean active) {
        Ui.runOnUi(() -> peer.setActive(active));
        return this;
    }

    public boolean isActive() {
        return Ui.onUi(peer::isActive);
    }

    public Spinner start() { return active(true); }
    public Spinner stop()  { return active(false); }

    @Override public Spinner tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Spinner dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Spinner acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public SpinnerPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
