package io.github.swat.backend.macos;

import io.github.swat.spi.MenuConfig;
import io.github.swat.spi.MenuItemPeer;
import io.github.swat.spi.MenuPeer;

import java.lang.foreign.MemorySegment;

final class MacosMenuPeer implements MenuPeer {

    private final MemorySegment menu;       // NSMenu*, retained
    private MemorySegment hostingItem;      // NSMenuItem* — set when this menu becomes a submenu of another menu
    private String title;

    MacosMenuPeer(MenuConfig cfg) {
        this.title = cfg.title() == null ? "" : cfg.title();
        // [[NSMenu alloc] initWithTitle:title]
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSMenu"));
        MemorySegment m = Objc.sendPtr(alloc, Objc.sel("initWithTitle:"), NSString.from(title));
        this.menu = m; // alloc+init returns +1 retained
        // Disable auto-enable so submenu state matches what we set explicitly.
        Objc.sendVoidBool(menu, Objc.sel("setAutoenablesItems:"), false);
    }

    MemorySegment nsMenu() { return menu; }
    String title() { return title; }

    @Override
    public void setTitle(String t) {
        this.title = t == null ? "" : t;
        Objc.sendVoid(menu, Objc.sel("setTitle:"), NSString.from(title));
        if (hostingItem != null) {
            Objc.sendVoid(hostingItem, Objc.sel("setTitle:"), NSString.from(title));
        }
    }

    @Override
    public void append(MenuItemPeer child) {
        switch (child) {
            case MacosMenuActionPeer a ->
                Objc.sendVoid(menu, Objc.sel("addItem:"), a.menuItem());
            case MacosMenuSeparatorPeer s ->
                Objc.sendVoid(menu, Objc.sel("addItem:"), s.menuItem());
            case MacosMenuPeer sub -> attachSubmenu(sub);
            default -> throw new IllegalArgumentException("Unknown menu item peer: " + child);
        }
    }

    private void attachSubmenu(MacosMenuPeer sub) {
        // Wrap submenu in an NSMenuItem with the submenu's title, then set submenu and add.
        MemorySegment itemAlloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        MemorySegment hostItem;
        try {
            // initWithTitle:action:keyEquivalent: — action=NULL for submenu host
            hostItem = (MemorySegment) Objc.msgSend(java.lang.foreign.FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    itemAlloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    NSString.from(sub.title()),
                    Objc.NIL,
                    NSString.from(""));
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(hostItem, Objc.sel("setSubmenu:"), sub.menu);
        Objc.sendVoid(menu, Objc.sel("addItem:"), hostItem);
        // hostItem retained by the parent menu via addItem; we drop our +1 from alloc/init.
        Objc.sendVoid(hostItem, Objc.sel("release"));
        sub.hostingItem = hostItem;
    }

    @Override
    public void close() {
        Objc.sendVoid(menu, Objc.sel("release"));
    }
}
