package cc.nawt.backend.macos;

import cc.nawt.spi.SwitchConfig;
import cc.nawt.spi.SwitchPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

/** NSSwitch (10.15+). Modern toggle control. */
final class MacosSwitchPeer implements SwitchPeer {

    private final MemorySegment view;
    private final MemorySegment target;
    private volatile Consumer<Boolean> trigger;

    MacosSwitchPeer(SwitchConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newToggleTarget(), Objc.sel("retain"));

        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSSwitch"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidLong(v, Objc.sel("setState:"), cfg.initialOn() ? 1L : 0L);

        Objc.sendVoid(v, Objc.sel("setTarget:"), target);
        Objc.sendVoid(v, Objc.sel("setAction:"), Delegates.TOGGLE_ACTION_SEL);
        this.view = v;

        Delegates.TOGGLE_HANDLERS.put(target.address(), () -> {
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(isOn()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment view() { return view; }

    @Override public void setOn(boolean on) {
        Objc.sendVoidLong(view, Objc.sel("setState:"), on ? 1L : 0L);
    }

    @Override public boolean isOn() {
        return Objc.sendLong(view, Objc.sel("state")) != 0;
    }

    @Override public void onToggle(Consumer<Boolean> trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.TOGGLE_HANDLERS.remove(target.address());
        Objc.sendVoid(view, Objc.sel("setTarget:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
