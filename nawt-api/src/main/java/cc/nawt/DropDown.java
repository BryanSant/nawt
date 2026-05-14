package cc.nawt;

import cc.nawt.event.SelectionEvent;
import cc.nawt.spi.DropDownConfig;
import cc.nawt.spi.DropDownPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class DropDown implements Widget {

    private final DropDownPeer peer;
    private volatile List<String> items;
    private final List<Consumer<SelectionEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SelectionEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private DropDown(DropDownPeer peer, List<String> items) {
        this.peer = peer;
        this.items = items;
        peer.onSelectionChange(this::dispatch);
    }

    public static DropDown of(String... items) { return of(List.of(items)); }

    public static DropDown of(List<String> items) {
        return Ui.onUi(() -> {
            List<String> snapshot = items == null ? List.of() : List.copyOf(items);
            int initial = snapshot.isEmpty() ? -1 : 0;
            DropDownPeer p = Toolkit.requireLaunched().peerFactory()
                .createDropDown(new DropDownConfig(snapshot, initial));
            return new DropDown(p, snapshot);
        });
    }

    public DropDown items(List<String> newItems) {
        List<String> snapshot = newItems == null ? List.of() : List.copyOf(newItems);
        Ui.runOnUi(() -> {
            this.items = snapshot;
            peer.setItems(snapshot);
        });
        return this;
    }

    public List<String> items() { return items; }

    public int selectedIndex() {
        return Ui.onUi(peer::selectedIndex);
    }

    public DropDown select(int index) {
        Ui.runOnUi(() -> peer.setSelectedIndex(index));
        return this;
    }

    public DropDown onSelectionChange(Consumer<SelectionEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public DropDown onSelectionChangeSync(Consumer<SelectionEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(int index) {
        String value = (index >= 0 && index < items.size()) ? items.get(index) : null;
        SelectionEvent e = new SelectionEvent(this, index, value);
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

    @Override public DropDown tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public DropDown dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public DropDown acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public DropDownPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
