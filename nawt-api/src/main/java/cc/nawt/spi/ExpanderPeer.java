package cc.nawt.spi;

import java.util.function.Consumer;

public non-sealed interface ExpanderPeer extends Peer {
    void setTitle(String title);
    void setContent(Peer content);
    void setExpanded(boolean expanded);
    boolean isExpanded();
    void onExpandedChange(Consumer<Boolean> trigger);
}
