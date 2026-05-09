package io.github.swat.event;

import io.github.swat.Window;

public final class WindowCloseEvent extends VetoableEvent {
    private final Window source;

    public WindowCloseEvent(Window source) {
        this.source = source;
    }

    public Window source() { return source; }
}
