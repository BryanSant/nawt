package io.github.swat.backend.macos;

import io.github.swat.spi.MenuPeer;
import io.github.swat.spi.SystemTrayConfig;
import io.github.swat.spi.SystemTrayPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * System tray icon backed by {@code NSStatusItem} from {@code [NSStatusBar
 * systemStatusBar]}. Variable-length status item (auto-sized to content);
 * icon is set as a template image so AppKit handles dark-mode tinting.
 */
final class MacosSystemTrayPeer implements SystemTrayPeer {

    /** {@code NSVariableStatusItemLength = -1.0}. */
    private static final double VARIABLE_LENGTH = -1.0;

    private final MemorySegment statusItem;     // NSStatusItem, retained by [statusItemWithLength:]
    private final MemorySegment statusBar;      // NSStatusBar shared instance — needed to remove the item

    MacosSystemTrayPeer(SystemTrayConfig cfg) {
        this.statusBar = Objc.sendPtr(Objc.cls("NSStatusBar"), Objc.sel("systemStatusBar"));
        // [statusBar statusItemWithLength:NSVariableStatusItemLength] — autoreleased,
        // but the status bar holds a strong reference until removeStatusItem:.
        try {
            FunctionDescriptor fd = FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT);
            this.statusItem = (MemorySegment) Objc.msgSend(fd).invoke(
                statusBar, Objc.sel("statusItemWithLength:"), VARIABLE_LENGTH);
        } catch (Throwable t) { throw new RuntimeException(t); }
        // Hold our own strong reference so the item survives if the caller's
        // close() detaches it from the bar.
        Objc.sendPtr(statusItem, Objc.sel("retain"));

        if (cfg.iconPath() != null) setIconPath(cfg.iconPath());
        if (cfg.tooltip() != null) setTooltip(cfg.tooltip());
    }

    @Override public void setIconPath(String path) {
        MemorySegment button = Objc.sendPtr(statusItem, Objc.sel("button"));
        if (button == null || button.address() == 0) return;
        if (path == null || path.isEmpty()) {
            Objc.sendVoid(button, Objc.sel("setImage:"), Objc.NIL);
            return;
        }
        // [[NSImage alloc] initWithContentsOfFile:nsPath]
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSImage"));
        MemorySegment img = Objc.sendPtr(alloc, Objc.sel("initWithContentsOfFile:"),
            NSString.from(path));
        if (img == null || img.address() == 0) return;
        // Template image — system tints for dark/light mode automatically.
        Objc.sendVoidBool(img, Objc.sel("setTemplate:"), true);
        // Constrain to the menu-bar icon size (~18 px on standard menu bar).
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            var size = arena.allocate(java.lang.foreign.MemoryLayout.structLayout(
                ValueLayout.JAVA_DOUBLE.withName("w"),
                ValueLayout.JAVA_DOUBLE.withName("h")));
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 18.0);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 18.0);
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR,
                        java.lang.foreign.MemoryLayout.structLayout(
                            ValueLayout.JAVA_DOUBLE.withName("w"),
                            ValueLayout.JAVA_DOUBLE.withName("h"))))
                    .invoke(img, Objc.sel("setSize:"), size);
            } catch (Throwable t) { /* size is cosmetic */ }
        }
        Objc.sendVoid(button, Objc.sel("setImage:"), img);
        // The button retains its image; release ours.
        Objc.sendVoid(img, Objc.sel("release"));
    }

    @Override public void setTooltip(String tooltip) {
        MemorySegment button = Objc.sendPtr(statusItem, Objc.sel("button"));
        if (button == null || button.address() == 0) return;
        MemorySegment ns = (tooltip == null || tooltip.isEmpty())
            ? Objc.NIL : NSString.from(tooltip);
        Objc.sendVoid(button, Objc.sel("setToolTip:"), ns);
    }

    @Override public void setMenu(MenuPeer menu) {
        if (menu == null) {
            Objc.sendVoid(statusItem, Objc.sel("setMenu:"), Objc.NIL);
            return;
        }
        if (!(menu instanceof MacosMenuPeer mmp)) {
            throw new IllegalArgumentException("Foreign MenuPeer: " + menu.getClass());
        }
        Objc.sendVoid(statusItem, Objc.sel("setMenu:"), mmp.nsMenu());
    }

    @Override public void close() {
        Objc.sendVoid(statusBar, Objc.sel("removeStatusItem:"), statusItem);
        Objc.sendVoid(statusItem, Objc.sel("release"));
    }
}
