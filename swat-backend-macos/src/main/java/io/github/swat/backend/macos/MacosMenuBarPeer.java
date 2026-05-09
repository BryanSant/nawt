package io.github.swat.backend.macos;

import io.github.swat.spi.MenuBarPeer;
import io.github.swat.spi.MenuPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

/**
 * macOS main menu — an NSMenu installed on {@code NSApp.mainMenu}.
 *
 * <p>The first item is a synthetic "app menu" containing a Quit item. macOS
 * always renders the first menu using the app's process name (so user-provided
 * top-level menus retain their own labels).
 */
final class MacosMenuBarPeer implements MenuBarPeer {

    private final MemorySegment mainMenu; // NSMenu*, retained

    MacosMenuBarPeer() {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSMenu"));
        MemorySegment m = Objc.sendPtr(alloc, Objc.sel("initWithTitle:"), NSString.from(""));
        this.mainMenu = m;
        Objc.sendVoidBool(mainMenu, Objc.sel("setAutoenablesItems:"), false);
        installAppMenu();
    }

    MemorySegment nsMenu() { return mainMenu; }

    private void installAppMenu() {
        // App menu: title is replaced by the app name automatically by macOS
        MemorySegment appMenu = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSMenu")), Objc.sel("initWithTitle:"), NSString.from("App"));

        // Quit Cmd+Q → [NSApp terminate:nil]
        MemorySegment quitAlloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        MemorySegment quitItem;
        try {
            quitItem = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    quitAlloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    NSString.from("Quit"),
                    Objc.sel("terminate:"),
                    NSString.from("q"));
        } catch (Throwable t) { throw new RuntimeException(t); }
        // target = nil → NSApp uses responder chain → NSApplication handles terminate:
        Objc.sendVoid(appMenu, Objc.sel("addItem:"), quitItem);
        Objc.sendVoid(quitItem, Objc.sel("release"));

        // Wrap appMenu as a top-level item on mainMenu
        MemorySegment hostAlloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        MemorySegment host;
        try {
            host = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    hostAlloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    NSString.from(""),
                    Objc.NIL,
                    NSString.from(""));
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(host, Objc.sel("setSubmenu:"), appMenu);
        Objc.sendVoid(mainMenu, Objc.sel("addItem:"), host);
        Objc.sendVoid(host, Objc.sel("release"));
        Objc.sendVoid(appMenu, Objc.sel("release"));
    }

    @Override
    public void addMenu(MenuPeer menu) {
        if (!(menu instanceof MacosMenuPeer mp)) {
            throw new IllegalArgumentException("Foreign MenuPeer: " + menu.getClass());
        }
        // Wrap user menu in a top-level NSMenuItem
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        MemorySegment item;
        try {
            item = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    alloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    NSString.from(mp.title()),
                    Objc.NIL,
                    NSString.from(""));
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(item, Objc.sel("setSubmenu:"), mp.nsMenu());
        Objc.sendVoid(mainMenu, Objc.sel("addItem:"), item);
        Objc.sendVoid(item, Objc.sel("release"));
    }

    @Override
    public void close() {
        Objc.sendVoid(mainMenu, Objc.sel("release"));
    }
}
