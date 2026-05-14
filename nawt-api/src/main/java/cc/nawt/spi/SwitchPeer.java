package cc.nawt.spi;

import java.util.function.Consumer;

public non-sealed interface SwitchPeer extends Peer {
    void setOn(boolean on);
    boolean isOn();
    void onToggle(Consumer<Boolean> trigger);
}
