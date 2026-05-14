package cc.nawt;

import cc.nawt.spi.DividerConfig;
import cc.nawt.spi.DividerPeer;
import cc.nawt.spi.Orientation;

/**
 * A 1-pt native separator line. Maps to {@code NSBox} of style "separator"
 * on macOS and {@code GtkSeparator} on GTK. Use inside a {@link Column} or
 * {@link Row} to visually divide groups of content (e.g. a "Park, State"
 * row from the description).
 */
public final class Divider implements Widget {

    private final DividerPeer peer;

    private Divider(DividerPeer peer) { this.peer = peer; }

    /** Horizontal rule — a thin line spanning the cross axis of a Column. */
    public static Divider horizontal() {
        return Ui.onUi(() -> {
            DividerPeer p = Toolkit.requireLaunched().peerFactory()
                .createDivider(new DividerConfig(Orientation.HORIZONTAL));
            return new Divider(p);
        });
    }

    /** Vertical rule — a thin line spanning the cross axis of a Row. */
    public static Divider vertical() {
        return Ui.onUi(() -> {
            DividerPeer p = Toolkit.requireLaunched().peerFactory()
                .createDivider(new DividerConfig(Orientation.VERTICAL));
            return new Divider(p);
        });
    }

    @Override public Divider tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Divider dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Divider acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public DividerPeer peer() { return peer; }

    @Override public void close() { Ui.runOnUi(peer::close); }
}
