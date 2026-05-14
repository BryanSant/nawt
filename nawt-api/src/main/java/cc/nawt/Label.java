package cc.nawt;

import cc.nawt.spi.LabelConfig;
import cc.nawt.spi.LabelPeer;

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

    /** Set the label's font point size. {@code 0} restores the platform default. */
    public Label fontSize(int points) {
        Ui.runOnUi(() -> peer.setFontSize(points));
        return this;
    }

    /** Render using the platform's monospaced system font. */
    public Label monospace() { return monospace(true); }

    public Label monospace(boolean on) {
        Ui.runOnUi(() -> peer.setMonospace(on));
        return this;
    }

    /** Shorthand for {@code style(LabelStyle.SECONDARY)} — secondary/subtitle text. */
    public Label secondary() { return style(LabelStyle.SECONDARY); }

    public Label style(LabelStyle style) {
        Ui.runOnUi(() -> peer.setStyle(style));
        return this;
    }

    @Override public Label tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Label dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Label acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public LabelPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
