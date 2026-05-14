package cc.nawt.backend.macos;

import cc.nawt.spi.MenuBarPeer;
import cc.nawt.spi.MenuPeer;

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
        // App menu: title is replaced by the app name automatically by macOS.
        MemorySegment appMenu = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSMenu")), Objc.sel("initWithTitle:"), NSString.from("App"));
        // Without this, AppKit auto-disables items whose target/action it
        // can't validate, including our custom nawtAppAbout: action.
        Objc.sendVoidBool(appMenu, Objc.sel("setAutoenablesItems:"), false);

        // Fetch the current process name (set by Toolkit.launch's appName via
        // -[NSProcessInfo setProcessName:]). Used to build "About <name>" /
        // "Quit <name>" labels — matches Cocoa convention.
        MemorySegment processInfo = Objc.sendPtr(Objc.cls("NSProcessInfo"), Objc.sel("processInfo"));
        MemorySegment nsName = Objc.sendPtr(processInfo, Objc.sel("processName"));
        String appName = NSString.toJava(nsName);
        if (appName == null || appName.isEmpty()) appName = "Application";

        // About <name> → nawtAppAbout: on a shared NawtAppAboutTarget.
        MemorySegment aboutTarget = Objc.sendPtr(Delegates.newAppAboutTarget(), Objc.sel("retain"));
        MemorySegment aboutAlloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        MemorySegment aboutItem;
        try {
            aboutItem = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    aboutAlloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    NSString.from("About " + appName),
                    Delegates.APP_ABOUT_ACTION_SEL,
                    NSString.from(""));
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(aboutItem, Objc.sel("setTarget:"), aboutTarget);
        Objc.sendVoid(appMenu, Objc.sel("addItem:"), aboutItem);
        Objc.sendVoid(aboutItem, Objc.sel("release"));

        // Separator (NSMenuItem.separatorItem is an autoreleased shared item).
        MemorySegment separator = Objc.sendPtr(Objc.cls("NSMenuItem"), Objc.sel("separatorItem"));
        Objc.sendVoid(appMenu, Objc.sel("addItem:"), separator);

        // Quit <name> Cmd+Q → [NSApp terminate:nil] via responder chain.
        MemorySegment quitAlloc = Objc.send_alloc(Objc.cls("NSMenuItem"));
        MemorySegment quitItem;
        try {
            quitItem = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    quitAlloc,
                    Objc.sel("initWithTitle:action:keyEquivalent:"),
                    NSString.from("Quit " + appName),
                    Objc.sel("terminate:"),
                    NSString.from("q"));
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(appMenu, Objc.sel("addItem:"), quitItem);
        Objc.sendVoid(quitItem, Objc.sel("release"));

        // Wrap appMenu as a top-level item on mainMenu.
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
