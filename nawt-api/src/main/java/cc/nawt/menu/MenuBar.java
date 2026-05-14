package cc.nawt.menu;

import cc.nawt.Toolkit;
import cc.nawt.Ui;
import cc.nawt.spi.MenuBarPeer;

import java.util.List;

/** A top-level menu bar. Attach via {@code Window.builder().menuBar(...)} or {@code Window.menuBar(...)}. */
public final class MenuBar implements AutoCloseable {

    private final MenuBarPeer peer;
    private final List<Menu> menus;

    private MenuBar(MenuBarPeer peer, List<Menu> menus) {
        this.peer = peer;
        this.menus = menus;
    }

    public static MenuBar of(Menu... menus) {
        List<Menu> snapshot = List.of(menus);
        return Ui.onUi(() -> {
            MenuBarPeer p = Toolkit.requireLaunched().peerFactory().createMenuBar();
            for (Menu m : snapshot) p.addMenu(m.peer());
            return new MenuBar(p, snapshot);
        });
    }

    public List<Menu> menus() { return menus; }
    public MenuBarPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (Menu m : menus) m.close();
            peer.close();
        });
    }
}
