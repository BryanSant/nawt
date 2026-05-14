package cc.nawt.spi;

import java.util.List;
import java.util.function.IntConsumer;

public non-sealed interface SidebarPeer extends Peer {
    /** Replace the displayed rows. Caller owns the lifecycle of the {@link Peer}s. */
    void setRows(List<Peer> rowPeers);

    /** Currently-selected row index, or {@code -1} for no selection. */
    int selectedIndex();

    /** Programmatically select a row. {@code -1} clears the selection. */
    void setSelectedIndex(int index);

    /** Register a single trigger fired on UI thread with the new selected index. */
    void onSelectionChange(IntConsumer trigger);
}
