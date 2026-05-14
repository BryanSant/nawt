package cc.nawt;

import cc.nawt.event.SelectionEvent;
import cc.nawt.spi.ListViewConfig;
import cc.nawt.spi.ListViewPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class ListView implements Widget {

    private final ListViewPeer peer;
    private volatile List<String> items;
    private final List<Consumer<SelectionEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SelectionEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private ListView(ListViewPeer peer, List<String> items) {
        this.peer = peer;
        this.items = items;
        peer.onSelectionChange(this::dispatch);
    }

    public static ListView of(String... items) { return of(List.of(items)); }

    public static ListView of(List<String> items) {
        return Ui.onUi(() -> {
            List<String> snapshot = items == null ? List.of() : List.copyOf(items);
            ListViewPeer p = Toolkit.requireLaunched().peerFactory()
                .createListView(new ListViewConfig(snapshot, -1, 8));
            return new ListView(p, snapshot);
        });
    }

    public ListView items(List<String> newItems) {
        List<String> snapshot = newItems == null ? List.of() : List.copyOf(newItems);
        Ui.runOnUi(() -> {
            this.items = snapshot;
            peer.setItems(snapshot);
        });
        return this;
    }

    public List<String> items() { return items; }

    /** Index of the currently selected row, or {@code -1} if none. */
    public int selectedIndex() {
        return Ui.onUi(peer::selectedIndex);
    }

    public ListView select(int index) {
        Ui.runOnUi(() -> peer.setSelectedIndex(index));
        return this;
    }

    public ListView clearSelection() { return select(-1); }

    public ListView onSelectionChange(Consumer<SelectionEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public ListView onSelectionChangeSync(Consumer<SelectionEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(int newIndex) {
        String value = (newIndex >= 0 && newIndex < items.size()) ? items.get(newIndex) : null;
        SelectionEvent e = new SelectionEvent(this, newIndex, value);
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

    @Override public ListView tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public ListView dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public ListView acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public ListViewPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
