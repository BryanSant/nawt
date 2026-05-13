package io.github.swat.spi;

/**
 * Opaque handle to a backend's native peer. Returned from {@code Widget.peer()}
 * as the public escape hatch.
 *
 * <p><strong>Stability.</strong> The concrete sub-interfaces here
 * ({@code WindowPeer}, {@code ButtonPeer}, …) are SPI surfaces consumed only
 * by backend implementors; their shape may evolve between SWAT minor versions
 * and downstream consumers must not implement them. The backend-specific
 * implementation classes (e.g. {@code MacosButtonPeer}, {@code GtkButtonPeer})
 * are even less stable — they may change between patch versions.
 *
 * <p><strong>Casting policy.</strong> Casting a {@code Peer} to a backend
 * implementation class is supported for code that is intentionally targeting a
 * specific backend (the backend module name —
 * {@code io.github.swat.backend.macos} / {@code io.github.swat.backend.gtk}
 * — is part of the contract). Casting in cross-platform code is a smell;
 * use the upcoming Capability registry instead.
 */
public sealed interface Peer extends AutoCloseable
    permits WindowPeer, LabelPeer, ButtonPeer, TextFieldPeer, ContainerPeer, ListViewPeer,
            CheckboxPeer, SwitchPeer, RadioPeer,
            SliderPeer, ProgressBarPeer, SpinnerPeer,
            DropDownPeer,
            FramePeer, ScrollContainerPeer, TabsPeer, SplitterPeer, ExpanderPeer, GridPeer,
            TreePeer, ImagePeer, CanvasPeer,
            HeaderBarPeer, SystemTrayPeer {

    @Override
    void close();
}
