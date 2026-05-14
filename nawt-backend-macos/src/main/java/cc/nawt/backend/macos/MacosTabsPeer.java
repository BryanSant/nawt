package cc.nawt.backend.macos;

import cc.nawt.spi.Peer;
import cc.nawt.spi.TabsConfig;
import cc.nawt.spi.TabsPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.IntConsumer;

/** NSTabView with NSTabViewItem children. */
final class MacosTabsPeer implements TabsPeer {

    private final MemorySegment view;
    private final MemorySegment delegate;
    private volatile IntConsumer trigger;

    MacosTabsPeer(TabsConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSTabView"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        this.view = v;

        this.delegate = Objc.sendPtr(Delegates.newTabsDelegate(), Objc.sel("retain"));
        Objc.sendVoid(view, Objc.sel("setDelegate:"), delegate);
        Delegates.TABS_HANDLERS.put(delegate.address(), () -> {
            IntConsumer t = trigger;
            if (t != null) {
                try { t.accept(selectedTab()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment view() { return view; }

    @Override public void appendTab(String title, Peer content) {
        MemorySegment item = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSTabViewItem")),
            Objc.sel("initWithIdentifier:"), NSString.from(title));
        Objc.sendVoid(item, Objc.sel("setLabel:"), NSString.from(title));
        Objc.sendVoid(item, Objc.sel("setView:"), MacosContainerPeer.peerView(content));
        Objc.sendVoid(view, Objc.sel("addTabViewItem:"), item);
        Objc.sendVoid(item, Objc.sel("release"));
    }

    @Override public int selectedTab() {
        MemorySegment selected = Objc.sendPtr(view, Objc.sel("selectedTabViewItem"));
        if (selected.address() == 0) return -1;
        return (int) Objc.sendLong(view, Objc.sel("indexOfTabViewItem:"), selected);
    }

    @Override public void selectTab(int index) {
        Objc.sendVoidLong(view, Objc.sel("selectTabViewItemAtIndex:"), index);
    }

    @Override public void onTabChange(IntConsumer trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.TABS_HANDLERS.remove(delegate.address());
        Objc.sendVoid(view, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(delegate, Objc.sel("release"));
    }
}
