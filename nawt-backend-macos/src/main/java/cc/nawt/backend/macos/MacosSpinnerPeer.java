package cc.nawt.backend.macos;

import cc.nawt.spi.SpinnerConfig;
import cc.nawt.spi.SpinnerPeer;

import java.lang.foreign.MemorySegment;

/** Indeterminate NSProgressIndicator in spinning style. */
final class MacosSpinnerPeer implements SpinnerPeer {

    private static final long NS_PROGRESS_INDICATOR_SPINNING = 1L;

    private final MemorySegment view;
    private boolean active;

    MacosSpinnerPeer(SpinnerConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSProgressIndicator"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidLong(v, Objc.sel("setStyle:"), NS_PROGRESS_INDICATOR_SPINNING);
        Objc.sendVoidBool(v, Objc.sel("setIndeterminate:"), true);
        Objc.sendVoidBool(v, Objc.sel("setDisplayedWhenStopped:"), false);
        this.view = v;
        if (cfg.initialActive()) setActive(true);
    }

    MemorySegment view() { return view; }

    @Override public void setActive(boolean on) {
        if (this.active == on) return;
        this.active = on;
        if (on) Objc.sendVoid(view, Objc.sel("startAnimation:"), Objc.NIL);
        else    Objc.sendVoid(view, Objc.sel("stopAnimation:"), Objc.NIL);
    }

    @Override public boolean isActive() { return active; }

    @Override public void close() {
        if (active) Objc.sendVoid(view, Objc.sel("stopAnimation:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
