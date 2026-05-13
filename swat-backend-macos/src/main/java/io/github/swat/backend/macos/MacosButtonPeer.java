package io.github.swat.backend.macos;

import io.github.swat.spi.ButtonConfig;
import io.github.swat.spi.ButtonPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

final class MacosButtonPeer implements ButtonPeer {

    private final MemorySegment view;   // NSButton, retained
    private final MemorySegment target; // SwatButtonTarget, retained

    MacosButtonPeer(ButtonConfig config) {
        this.target = Objc.sendPtr(Delegates.newButtonTarget(), Objc.sel("retain"));

        // +[NSButton buttonWithTitle:target:action:]  (id) (id title, id target, SEL action)
        try {
            MemorySegment v = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    Objc.cls("NSButton"),
                    Objc.sel("buttonWithTitle:target:action:"),
                    NSString.from(config.text()),
                    target,
                    Delegates.BUTTON_ACTION_SEL);
            this.view = Objc.sendPtr(v, Objc.sel("retain"));
        } catch (Throwable t) { throw new RuntimeException(t); }

        if (config.fontSize() > 0) {
            applyFontSize(view, config.fontSize());
        }
    }

    MemorySegment view() { return view; }

    @Override
    public void setText(String text) {
        Objc.sendVoid(view, Objc.sel("setTitle:"), NSString.from(text));
    }

    @Override
    public void onClick(Runnable trigger) {
        Delegates.BUTTON_HANDLERS.put(target.address(), trigger);
    }

    @Override
    public void setFontSize(int points) {
        applyFontSize(view, points);
    }

    /** NSBezelStyleFlexiblePush (formerly NSBezelStyleRegularSquare) — the
     *  bezel style whose intrinsic height tracks its title font. The default
     *  bezel (NSBezelStylePush, value 1) is a fixed-height rounded button and
     *  will let an enlarged font overflow vertically. */
    private static final long NS_BEZEL_FLEXIBLE_PUSH = 2L;

    /**
     * Apply a system font of the given point size to {@code view}. When a
     * non-default size is requested, also switch the bezel style so the
     * button's intrinsic height grows to fit the new font.
     * {@code points <= 0} restores the default system font and leaves the
     * bezel style untouched.
     */
    private static void applyFontSize(MemorySegment view, int points) {
        double size = points <= 0 ? 0.0 : (double) points;
        MemorySegment font;
        try {
            font = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(Objc.cls("NSFont"), Objc.sel("systemFontOfSize:"), size);
        } catch (Throwable t) { throw new RuntimeException(t); }
        if (points > 0) {
            Objc.sendVoidLong(view, Objc.sel("setBezelStyle:"), NS_BEZEL_FLEXIBLE_PUSH);
        }
        Objc.sendVoid(view, Objc.sel("setFont:"), font);
    }

    @Override
    public void close() {
        Delegates.BUTTON_HANDLERS.remove(target.address());
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
