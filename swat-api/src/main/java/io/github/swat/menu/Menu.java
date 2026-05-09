package io.github.swat.menu;

import io.github.swat.Toolkit;
import io.github.swat.Ui;
import io.github.swat.spi.MenuConfig;
import io.github.swat.spi.MenuPeer;

import java.util.ArrayList;
import java.util.List;

/** A native menu — used as a top-level menu in a {@link MenuBar} or as a submenu nested in another menu. */
public final class Menu implements MenuItem {

    private final MenuPeer peer;
    private final List<MenuItem> items;

    private Menu(MenuPeer peer, List<MenuItem> items) {
        this.peer = peer;
        this.items = items;
    }

    public static Menu of(String title, MenuItem... items) {
        return builder(title).items(items).build();
    }

    public static Builder builder(String title) { return new Builder(title); }

    public String title() { return ""; /* peer-side; could be tracked */ }
    public List<MenuItem> items() { return items; }

    @Override public MenuPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (MenuItem it : items) it.close();
            peer.close();
        });
    }

    public static final class Builder {
        private final String title;
        private final List<MenuItem> kids = new ArrayList<>();

        private Builder(String title) { this.title = title; }

        public Builder add(MenuItem item) { kids.add(item); return this; }
        public Builder items(MenuItem... items) {
            for (MenuItem it : items) kids.add(it);
            return this;
        }
        public Builder action(String text, java.util.function.Consumer<io.github.swat.event.MenuEvent> onSelect) {
            kids.add(MenuAction.of(text).onSelect(onSelect));
            return this;
        }
        public Builder separator() {
            kids.add(MenuSeparator.of());
            return this;
        }

        public Menu build() {
            List<MenuItem> snapshot = List.copyOf(kids);
            return Ui.onUi(() -> {
                MenuPeer p = Toolkit.requireLaunched().peerFactory().createMenu(new MenuConfig(title));
                for (MenuItem it : snapshot) p.append(it.peer());
                return new Menu(p, snapshot);
            });
        }
    }
}
