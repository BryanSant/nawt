package cc.nawt.backend.macos;

import cc.nawt.spi.CheckboxConfig;
import cc.nawt.spi.CheckboxPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

/** NSButton with NSButtonTypeSwitch — macOS's native checkbox shape. */
final class MacosCheckboxPeer implements CheckboxPeer {

    private static final long NS_BUTTON_TYPE_SWITCH = 3L;

    private final MemorySegment view;
    private final MemorySegment target;
    private volatile Consumer<Boolean> trigger;

    MacosCheckboxPeer(CheckboxConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newToggleTarget(), Objc.sel("retain"));

        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSButton"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidLong(v, Objc.sel("setButtonType:"), NS_BUTTON_TYPE_SWITCH);
        Objc.sendVoid(v, Objc.sel("setTitle:"), NSString.from(cfg.text()));
        Objc.sendVoidLong(v, Objc.sel("setState:"), cfg.initialChecked() ? 1L : 0L);

        Objc.sendVoid(v, Objc.sel("setTarget:"), target);
        Objc.sendVoid(v, Objc.sel("setAction:"), Delegates.TOGGLE_ACTION_SEL);
        this.view = v;

        Delegates.TOGGLE_HANDLERS.put(target.address(), () -> {
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(isChecked()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment view() { return view; }

    @Override public void setText(String text) {
        Objc.sendVoid(view, Objc.sel("setTitle:"), NSString.from(text));
    }

    @Override public void setChecked(boolean checked) {
        Objc.sendVoidLong(view, Objc.sel("setState:"), checked ? 1L : 0L);
    }

    @Override public boolean isChecked() {
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
