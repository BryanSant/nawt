package io.github.swat.spi;

import java.util.function.Consumer;

public non-sealed interface CheckboxPeer extends Peer {
    void setText(String text);
    void setChecked(boolean checked);
    boolean isChecked();
    void onToggle(Consumer<Boolean> trigger);
}
