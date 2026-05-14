package cc.nawt.spi;

import java.util.function.Consumer;

public non-sealed interface RadioPeer extends Peer {
    void setText(String text);
    void setSelected(boolean selected);
    boolean isSelected();
    /** Group {@code other} with this peer; selecting one deselects siblings in the same group. */
    void groupWith(RadioPeer other);
    void onToggle(Consumer<Boolean> trigger);
}
