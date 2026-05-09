package io.github.swat.spi;

import java.util.List;
import java.util.function.IntConsumer;

public non-sealed interface DropDownPeer extends Peer {
    void setItems(List<String> items);
    int selectedIndex();
    void setSelectedIndex(int index);
    void onSelectionChange(IntConsumer trigger);
}
