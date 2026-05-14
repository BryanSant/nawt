package cc.nawt.menu;

import cc.nawt.Toolkit;
import cc.nawt.Ui;
import cc.nawt.spi.MenuSeparatorPeer;

public final class MenuSeparator implements MenuItem {

    private final MenuSeparatorPeer peer;

    private MenuSeparator(MenuSeparatorPeer peer) { this.peer = peer; }

    public static MenuSeparator of() {
        return Ui.onUi(() -> new MenuSeparator(
            Toolkit.requireLaunched().peerFactory().createMenuSeparator()));
    }

    @Override public MenuSeparatorPeer peer() { return peer; }

    @Override public void close() { Ui.runOnUi(peer::close); }
}
