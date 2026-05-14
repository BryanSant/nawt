package cc.nawt.event;

import cc.nawt.Sidebar;

/**
 * Fired when the user changes the selected row of a {@link Sidebar}.
 *
 * @param <T> the model type carried by the sidebar
 */
public final class SidebarSelectionEvent<T> {

    private final Sidebar<T> source;
    private final int index;
    private final T item;

    public SidebarSelectionEvent(Sidebar<T> source, int index, T item) {
        this.source = source;
        this.index = index;
        this.item = item;
    }

    public Sidebar<T> source() { return source; }

    /** Zero-based row index, or {@code -1} if the selection was cleared. */
    public int selectedIndex() { return index; }

    /** Selected item, or {@code null} if the selection was cleared. */
    public T selectedItem() { return item; }
}
