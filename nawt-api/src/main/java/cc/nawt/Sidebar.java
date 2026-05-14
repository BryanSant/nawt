package cc.nawt;

import cc.nawt.event.SidebarSelectionEvent;
import cc.nawt.spi.Peer;
import cc.nawt.spi.SidebarConfig;
import cc.nawt.spi.SidebarPeer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A vertical list with native source-list styling (vibrant translucent
 * background, accent-color highlight) intended for the leading pane of a
 * {@link NavigationSplit}. Each row's content is built by a caller-supplied
 * {@link Function} from the item to a {@link Widget}.
 *
 * <p>Requires {@link Capability#SIDEBAR} on the active backend. Backends
 * without it throw {@code UnsupportedOperationException} on construction.
 *
 * @param <T> the model type
 */
public final class Sidebar<T> implements Widget {

    private final SidebarPeer peer;
    private final Function<T, Widget> rowBuilder;
    private List<T> items;
    private List<Widget> rowWidgets;
    private final List<Consumer<SidebarSelectionEvent<T>>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SidebarSelectionEvent<T>>> syncListeners = new CopyOnWriteArrayList<>();

    private Sidebar(SidebarPeer peer, List<T> items, List<Widget> rowWidgets, Function<T, Widget> rowBuilder) {
        this.peer = peer;
        this.items = items;
        this.rowWidgets = rowWidgets;
        this.rowBuilder = rowBuilder;
        peer.onSelectionChange(this::dispatch);
    }

    public static <T> Sidebar<T> of(List<T> items, Function<T, Widget> rowBuilder) {
        if (rowBuilder == null) throw new IllegalArgumentException("rowBuilder must not be null");
        List<T> itemsSnap = items == null ? List.of() : List.copyOf(items);
        return Ui.onUi(() -> {
            List<Widget> kids = new ArrayList<>(itemsSnap.size());
            List<Peer> kidPeers = new ArrayList<>(itemsSnap.size());
            for (T item : itemsSnap) {
                Widget w = rowBuilder.apply(item);
                if (w == null) {
                    throw new IllegalStateException("rowBuilder returned null for item: " + item);
                }
                kids.add(w);
                kidPeers.add(w.peer());
            }
            SidebarPeer p = Toolkit.requireLaunched().peerFactory()
                .createSidebar(new SidebarConfig(kidPeers));
            return new Sidebar<>(p, itemsSnap, kids, rowBuilder);
        });
    }

    public List<T> items() { return items; }

    /** Replace the displayed items, rebuilding every row via the original {@code rowBuilder}. */
    public Sidebar<T> items(List<T> newItems) {
        List<T> snap = newItems == null ? List.of() : List.copyOf(newItems);
        Ui.runOnUi(() -> {
            // Build the new row widgets before tearing down the old ones, so a
            // failing rowBuilder doesn't leave us with a partial state.
            List<Widget> newRows = new ArrayList<>(snap.size());
            List<Peer> newPeers = new ArrayList<>(snap.size());
            for (T item : snap) {
                Widget w = rowBuilder.apply(item);
                if (w == null) {
                    throw new IllegalStateException("rowBuilder returned null for item: " + item);
                }
                newRows.add(w);
                newPeers.add(w.peer());
            }
            List<Widget> oldRows = this.rowWidgets;
            this.items = snap;
            this.rowWidgets = newRows;
            peer.setRows(newPeers);
            for (Widget old : oldRows) {
                try { old.close(); }
                catch (Throwable t) { t.printStackTrace(); }
            }
        });
        return this;
    }

    public int selectedIndex() { return Ui.onUi(peer::selectedIndex); }

    public T selectedItem() {
        int idx = selectedIndex();
        return (idx >= 0 && idx < items.size()) ? items.get(idx) : null;
    }

    public Sidebar<T> select(T item) {
        int idx = items.indexOf(item);
        Ui.runOnUi(() -> peer.setSelectedIndex(idx));
        return this;
    }

    public Sidebar<T> selectIndex(int index) {
        Ui.runOnUi(() -> peer.setSelectedIndex(index));
        return this;
    }

    /** Register a selection listener that runs on a fresh virtual thread. */
    public Sidebar<T> onSelectionChange(Consumer<SidebarSelectionEvent<T>> handler) {
        asyncListeners.add(handler);
        return this;
    }

    /** Register a selection listener that runs <em>synchronously on the UI thread</em>. */
    public Sidebar<T> onSelectionChangeSync(Consumer<SidebarSelectionEvent<T>> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(int index) {
        T item = (index >= 0 && index < items.size()) ? items.get(index) : null;
        SidebarSelectionEvent<T> e = new SidebarSelectionEvent<>(this, index, item);
        for (Consumer<SidebarSelectionEvent<T>> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<SidebarSelectionEvent<T>> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public Sidebar<T> tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public Sidebar<T> dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public Sidebar<T> acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public SidebarPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            for (Widget w : rowWidgets) {
                try { w.close(); }
                catch (Throwable t) { t.printStackTrace(); }
            }
            peer.close();
        });
    }
}
