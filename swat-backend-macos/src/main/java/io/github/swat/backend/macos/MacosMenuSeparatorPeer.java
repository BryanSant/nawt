package io.github.swat.backend.macos;

import io.github.swat.spi.MenuSeparatorPeer;

import java.lang.foreign.MemorySegment;

final class MacosMenuSeparatorPeer implements MenuSeparatorPeer {

    private final MemorySegment item; // NSMenuItem*, retained

    MacosMenuSeparatorPeer() {
        // +[NSMenuItem separatorItem] returns an autoreleased NSMenuItem
        MemorySegment sep = Objc.sendPtr(Objc.cls("NSMenuItem"), Objc.sel("separatorItem"));
        this.item = Objc.sendPtr(sep, Objc.sel("retain"));
    }

    MemorySegment menuItem() { return item; }

    @Override
    public void close() {
        Objc.sendVoid(item, Objc.sel("release"));
    }
}
