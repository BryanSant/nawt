package io.github.swat.spi;

public non-sealed interface MenuActionPeer extends MenuItemPeer {
    void setText(String text);
    void setEnabled(boolean enabled);
    /** Register the trigger fired on the UI thread when the user activates this item. */
    void onSelect(Runnable trigger);
}
