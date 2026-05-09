package io.github.swat.spi;

import java.util.function.IntConsumer;

public non-sealed interface TabsPeer extends Peer {
    void appendTab(String title, Peer content);
    int selectedTab();
    void selectTab(int index);
    void onTabChange(IntConsumer trigger);
}
