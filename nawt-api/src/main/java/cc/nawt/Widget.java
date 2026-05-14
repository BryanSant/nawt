package cc.nawt;

import cc.nawt.spi.Peer;

/**
 * Root of the abstract widget hierarchy. Every widget owns a backend
 * {@link Peer} that is the real native control underneath.
 */
public sealed interface Widget extends AutoCloseable
    permits Container, Label, Button, TextField, ListView,
            Checkbox, Switch, Radio,
            Slider, ProgressBar, Spinner,
            DropDown, Tree, Image, Canvas,
            Sidebar, Map, Divider {

    /**
     * Escape hatch: the underlying native peer.
     *
     * <p>Casting the result to a backend-specific subtype (e.g.
     * {@code MacosButtonPeer}, {@code GtkButtonPeer}) is supported but
     * <strong>unstable</strong> — backend internal types may change between
     * NAWT versions, even patch versions. The backend module names
     * ({@code cc.nawt.backend.macos}, {@code cc.nawt.backend.gtk})
     * are part of the contract for users targeting a specific platform.
     *
     * <p>Casting in cross-platform code is a smell. The portable answer is the
     * Capability registry (see {@code PeerFactory.capabilities()}); platform
     * specifics should be expressed as opt-in hints attached to widget configs,
     * not as conditional reflection on the peer's runtime class.
     */
    Peer peer();

    /** Set a hover tooltip on this widget; null or empty clears it. */
    default Widget tooltip(String text) {
        Ui.runOnUi(() -> Toolkit.requireLaunched().peerFactory().setTooltip(peer(), text));
        return this;
    }

    /**
     * Make this widget a drag source. The supplier is invoked at drag start to
     * produce the text payload; pass null to disable. Tier-1 supports text only.
     */
    default Widget dragText(java.util.function.Supplier<String> textProvider) {
        Ui.runOnUi(() -> Toolkit.requireLaunched().peerFactory().setDragSource(peer(), textProvider));
        return this;
    }

    /**
     * Make this widget a drop target. The handler is invoked when a drop
     * completes; pass null to disable. Tier-1 supports text only.
     */
    default Widget acceptText(java.util.function.Consumer<String> textHandler) {
        Ui.runOnUi(() -> Toolkit.requireLaunched().peerFactory().setDropTarget(peer(), textHandler));
        return this;
    }

    @Override
    void close();
}
