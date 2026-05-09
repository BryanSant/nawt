package io.github.swat.backend.macos;

import io.github.swat.spi.HeaderBarConfig;
import io.github.swat.spi.HeaderBarPeer;
import io.github.swat.spi.Peer;

import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Header bar backed by {@link com.apple.appkit NSToolbar} in unified style.
 * The window assumes responsibility for installing the toolbar via
 * {@link MacosWindowPeer#setHeaderBar}; we just own the {@code NSToolbar}
 * instance and its delegate.
 */
final class MacosHeaderBarPeer implements HeaderBarPeer {

    private final MemorySegment toolbar;
    private final MemorySegment delegate;
    private final List<MemorySegment> startViews = new ArrayList<>();
    private final List<MemorySegment> endViews = new ArrayList<>();

    MacosHeaderBarPeer(HeaderBarConfig cfg) {
        this.delegate = Objc.sendPtr(Delegates.newToolbarDelegate(), Objc.sel("retain"));
        Delegates.TOOLBAR_START_VIEWS.put(delegate.address(), startViews);
        Delegates.TOOLBAR_END_VIEWS.put(delegate.address(), endViews);

        // [[NSToolbar alloc] initWithIdentifier:@"swat.headerbar.<addr>"]
        MemorySegment id = NSString.from("swat.headerbar." + Long.toHexString(delegate.address()));
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSToolbar"));
        this.toolbar = Objc.sendPtr(alloc, Objc.sel("initWithIdentifier:"), id);
        Objc.sendVoid(toolbar, Objc.sel("setDelegate:"), delegate);
        Objc.sendVoidBool(toolbar, Objc.sel("setAllowsUserCustomization:"), false);
        Objc.sendVoidBool(toolbar, Objc.sel("setAutosavesConfiguration:"), false);
        // NSToolbarDisplayModeIconOnly = 2 — labels suppressed; the embedded view
        // renders itself.
        Objc.sendVoidLong(toolbar, Objc.sel("setDisplayMode:"), 2L);

        for (Peer p : cfg.startItems()) addStart(p);
        for (Peer p : cfg.endItems()) addEnd(p);
    }

    MemorySegment toolbar() { return toolbar; }

    @Override public void addStart(Peer item) {
        MemorySegment view = MacosContainerPeer.peerView(item);
        int idx = startViews.size();
        startViews.add(view);
        // [toolbar insertItemWithItemIdentifier:id atIndex:idx]
        try {
            java.lang.foreign.FunctionDescriptor fd = java.lang.foreign.FunctionDescriptor.ofVoid(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT);
            Objc.msgSend(fd).invoke(toolbar,
                Objc.sel("insertItemWithItemIdentifier:atIndex:"),
                NSString.from(Delegates.TOOLBAR_START_PREFIX + idx),
                (long) idx);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void addEnd(Peer item) {
        MemorySegment view = MacosContainerPeer.peerView(item);
        int idx = endViews.size();
        endViews.add(view);
        // Position is after start items + flexible space + existing end items.
        long position = startViews.size() + 1L + idx;
        try {
            java.lang.foreign.FunctionDescriptor fd = java.lang.foreign.FunctionDescriptor.ofVoid(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT);
            // Ensure flexible space is in the toolbar before the first end item.
            if (idx == 0) {
                Objc.msgSend(fd).invoke(toolbar,
                    Objc.sel("insertItemWithItemIdentifier:atIndex:"),
                    NSString.from(Delegates.TOOLBAR_FLEXIBLE_SPACE_ID),
                    (long) startViews.size());
                position = startViews.size() + 1L; // recompute with flex now present
            }
            Objc.msgSend(fd).invoke(toolbar,
                Objc.sel("insertItemWithItemIdentifier:atIndex:"),
                NSString.from(Delegates.TOOLBAR_END_PREFIX + idx),
                position);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void close() {
        Delegates.TOOLBAR_START_VIEWS.remove(delegate.address());
        Delegates.TOOLBAR_END_VIEWS.remove(delegate.address());
        Objc.sendVoid(toolbar, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(toolbar, Objc.sel("release"));
        Objc.sendVoid(delegate, Objc.sel("release"));
    }
}
