package io.github.swat.menu;

import io.github.swat.Toolkit;
import io.github.swat.Ui;
import io.github.swat.event.MenuEvent;
import io.github.swat.spi.MenuActionConfig;
import io.github.swat.spi.MenuActionPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class MenuAction implements MenuItem {

    private final MenuActionPeer peer;
    private final List<Consumer<MenuEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<MenuEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private MenuAction(MenuActionPeer peer) {
        this.peer = peer;
        peer.onSelect(this::dispatch);
    }

    public static MenuAction of(String text) {
        return Ui.onUi(() -> new MenuAction(
            Toolkit.requireLaunched().peerFactory().createMenuAction(
                new MenuActionConfig(text, null, true))));
    }

    public static MenuAction of(String text, String shortcut) {
        return Ui.onUi(() -> new MenuAction(
            Toolkit.requireLaunched().peerFactory().createMenuAction(
                new MenuActionConfig(text, shortcut, true))));
    }

    public MenuAction text(String text) {
        Ui.runOnUi(() -> peer.setText(text));
        return this;
    }

    public MenuAction enabled(boolean enabled) {
        Ui.runOnUi(() -> peer.setEnabled(enabled));
        return this;
    }

    /** Handler runs on a fresh virtual thread. */
    public MenuAction onSelect(Consumer<MenuEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    /** Handler runs synchronously on the UI thread. Use only for cheap work. */
    public MenuAction onSelectSync(Consumer<MenuEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch() {
        MenuEvent e = new MenuEvent(this);
        for (Consumer<MenuEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<MenuEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public MenuActionPeer peer() { return peer; }

    @Override public void close() { Ui.runOnUi(peer::close); }
}
