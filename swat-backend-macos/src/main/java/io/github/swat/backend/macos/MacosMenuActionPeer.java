package io.github.swat.backend.macos;

import io.github.swat.spi.MenuActionConfig;
import io.github.swat.spi.MenuActionPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

final class MacosMenuActionPeer implements MenuActionPeer {

    private final MemorySegment item;     // NSMenuItem*, retained
    private final MemorySegment target;   // SwatMenuActionTarget*, retained

    MacosMenuActionPeer(MenuActionConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newMenuTarget(), Objc.sel("retain"));

        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        // -[NSMenuItem initWithTitle:action:keyEquivalent:]
        // (id) initWithTitle:(NSString*)title action:(SEL)sel keyEquivalent:(NSString*)key
        MemorySegment titleNs = NSString.from(cfg.text());
        MemorySegment keyNs = NSString.from(cfg.shortcut() == null ? "" : cfg.shortcut());
        MemorySegment menuItem;
        try {
            menuItem = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    alloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    titleNs,
                    Delegates.MENU_ACTION_SEL,
                    keyNs);
        } catch (Throwable t) { throw new RuntimeException(t); }

        Objc.sendVoid(menuItem, Objc.sel("setTarget:"), target);
        if (!cfg.enabled()) Objc.sendVoidBool(menuItem, Objc.sel("setEnabled:"), false);

        this.item = menuItem; // initWith returns +1 retained
    }

    MemorySegment menuItem() { return item; }

    @Override
    public void setText(String text) {
        Objc.sendVoid(item, Objc.sel("setTitle:"), NSString.from(text));
    }

    @Override
    public void setEnabled(boolean enabled) {
        Objc.sendVoidBool(item, Objc.sel("setEnabled:"), enabled);
    }

    @Override
    public void onSelect(Runnable trigger) {
        Delegates.MENU_HANDLERS.put(target.address(), trigger);
    }

    @Override
    public void close() {
        Delegates.MENU_HANDLERS.remove(target.address());
        Objc.sendVoid(item, Objc.sel("setTarget:"), Objc.NIL);
        Objc.sendVoid(item, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
