package cc.nawt.spi;

import java.util.List;
import java.util.function.IntConsumer;

public non-sealed interface ListViewPeer extends Peer {
    void setItems(List<String> items);
    /** Index of the selected row, or -1 if no selection. */
    int selectedIndex();
    /** Set the selection; pass -1 to clear. */
    void setSelectedIndex(int index);
    /** Trigger fired on UI thread when the selection changes. Argument: new selected index, or -1. */
    void onSelectionChange(IntConsumer trigger);
}
