package cc.nawt;

import cc.nawt.spi.ProgressBarConfig;
import cc.nawt.spi.ProgressBarPeer;

public final class ProgressBar implements Widget {

    private final ProgressBarPeer peer;

    private ProgressBar(ProgressBarPeer peer) { this.peer = peer; }

    public static ProgressBar of() { return of(0); }

    public static ProgressBar of(double value) {
        return Ui.onUi(() -> {
            ProgressBarPeer p = Toolkit.requireLaunched().peerFactory()
                .createProgressBar(new ProgressBarConfig(value, false));
            return new ProgressBar(p);
        });
    }

    public static ProgressBar indeterminate() {
        return Ui.onUi(() -> {
            ProgressBarPeer p = Toolkit.requireLaunched().peerFactory()
                .createProgressBar(new ProgressBarConfig(0, true));
            return new ProgressBar(p);
        });
    }

    /** Set the displayed progress in [0, 1]. */
    public ProgressBar value(double value) {
        Ui.runOnUi(() -> peer.setValue(value));
        return this;
    }

    public ProgressBar indeterminate(boolean on) {
        Ui.runOnUi(() -> peer.setIndeterminate(on));
        return this;
    }

    @Override public ProgressBar tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public ProgressBar dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public ProgressBar acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public ProgressBarPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
