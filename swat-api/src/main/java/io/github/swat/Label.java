package io.github.swat;

import io.github.swat.spi.LabelConfig;
import io.github.swat.spi.LabelPeer;

public final class Label implements Widget {

    private final LabelPeer peer;

    private Label(LabelPeer peer) { this.peer = peer; }

    public static Label of(String text) {
        return Ui.onUi(() -> {
            LabelPeer p = Toolkit.requireLaunched().peerFactory()
                .createLabel(new LabelConfig(text));
            return new Label(p);
        });
    }

    public Label text(String text) {
        Ui.runOnUi(() -> peer.setText(text));
        return this;
    }

    public String text() {
        return Ui.onUi(peer::getText);
    }

    @Override public Label tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Label dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Label acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public LabelPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
