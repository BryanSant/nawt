package io.github.swat.backend.macos;

import io.github.swat.spi.DropDownConfig;
import io.github.swat.spi.DropDownPeer;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.function.IntConsumer;

/** NSPopUpButton in pop-up (non-pull-down) mode. */
final class MacosDropDownPeer implements DropDownPeer {

    private final MemorySegment view;
    private final MemorySegment target;
    private volatile IntConsumer trigger;

    MacosDropDownPeer(DropDownConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newToggleTarget(), Objc.sel("retain"));

        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSPopUpButton"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidBool(v, Objc.sel("setPullsDown:"), false);
        this.view = v;

        applyItems(cfg.items());
        if (cfg.initialSelection() >= 0) setSelectedIndex(cfg.initialSelection());

        Objc.sendVoid(view, Objc.sel("setTarget:"), target);
        Objc.sendVoid(view, Objc.sel("setAction:"), Delegates.TOGGLE_ACTION_SEL);

        Delegates.TOGGLE_HANDLERS.put(target.address(), () -> {
            IntConsumer t = trigger;
            if (t != null) {
                try { t.accept(selectedIndex()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment view() { return view; }

    private void applyItems(List<String> items) {
        Objc.sendVoid(view, Objc.sel("removeAllItems"));
        for (String item : items) {
            Objc.sendVoid(view, Objc.sel("addItemWithTitle:"), NSString.from(item == null ? "" : item));
        }
    }

    @Override public void setItems(List<String> items) {
        applyItems(items == null ? List.of() : items);
    }

    @Override public int selectedIndex() {
        return (int) Objc.sendLong(view, Objc.sel("indexOfSelectedItem"));
    }

    @Override public void setSelectedIndex(int index) {
        Objc.sendVoidLong(view, Objc.sel("selectItemAtIndex:"), index);
    }

    @Override public void onSelectionChange(IntConsumer trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.TOGGLE_HANDLERS.remove(target.address());
        Objc.sendVoid(view, Objc.sel("setTarget:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
