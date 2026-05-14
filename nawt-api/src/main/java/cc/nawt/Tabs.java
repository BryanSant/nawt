package cc.nawt;

import cc.nawt.event.SelectionEvent;
import cc.nawt.spi.TabsConfig;
import cc.nawt.spi.TabsPeer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Tabs implements Container {

    private final TabsPeer peer;
    private final List<String> titles;
    private final List<Widget> children;
    private final List<Consumer<SelectionEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SelectionEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private Tabs(TabsPeer peer, List<String> titles, List<Widget> children) {
        this.peer = peer;
        this.titles = titles;
        this.children = children;
        peer.onTabChange(this::dispatch);
    }

    public static Builder builder() { return new Builder(); }

    public List<Widget> children() { return children; }

    public int selected() { return Ui.onUi(peer::selectedTab); }

    public Tabs select(int index) {
        Ui.runOnUi(() -> peer.selectTab(index));
        return this;
    }

    public Tabs onTabChange(Consumer<SelectionEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    private void dispatch(int index) {
        String title = (index >= 0 && index < titles.size()) ? titles.get(index) : null;
        SelectionEvent e = new SelectionEvent(this, index, title);
        for (Consumer<SelectionEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<SelectionEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public Tabs tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Tabs dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Tabs acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public TabsPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (Widget child : children) child.close();
            peer.close();
        });
    }

    public static final class Builder {
        private final List<String> titles = new ArrayList<>();
        private final List<Widget> kids = new ArrayList<>();

        private Builder() {}

        public Builder tab(String title, Widget content) {
            titles.add(title == null ? "" : title);
            kids.add(content);
            return this;
        }

        public Tabs build() {
            List<String> tSnap = List.copyOf(titles);
            List<Widget> wSnap = List.copyOf(kids);
            return Ui.onUi(() -> {
                TabsPeer p = Toolkit.requireLaunched().peerFactory()
                    .createTabs(new TabsConfig());
                for (int i = 0; i < tSnap.size(); i++) {
                    p.appendTab(tSnap.get(i), wSnap.get(i).peer());
                }
                return new Tabs(p, tSnap, wSnap);
            });
        }
    }
}
