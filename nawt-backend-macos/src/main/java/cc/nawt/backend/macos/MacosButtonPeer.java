package cc.nawt.backend.macos;

import cc.nawt.SystemIcon;
import cc.nawt.spi.ButtonConfig;
import cc.nawt.spi.ButtonPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

final class MacosButtonPeer implements ButtonPeer {

    private final MemorySegment view;   // NSButton, retained
    private final MemorySegment target; // NawtButtonTarget, retained

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
        if (config.icon() != null) {
            setIcon(config.icon());
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

    @Override
    public void setIcon(SystemIcon icon) {
        if (icon == null) {
            Objc.sendVoid(view, Objc.sel("setImage:"), Objc.NIL);
            // NSImageLeading = 2 — reset image-position to leading even when
            // empty so a later setIcon() resumes leading-image rendering.
            Objc.sendVoidLong(view, Objc.sel("setImagePosition:"), 2L);
            return;
        }
        MemorySegment image;
        try {
            image = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(
                    Objc.cls("NSImage"),
                    Objc.sel("imageWithSystemSymbolName:accessibilityDescription:"),
                    NSString.from(icon.sfSymbolName()),
                    Objc.NIL);
        } catch (Throwable t) { throw new RuntimeException(t); }
        if (image == null || image.address() == 0) {
            // SF Symbol lookup failed (very old OS or removed symbol). Leave
            // the title intact and bail rather than crashing.
            return;
        }
        Objc.sendVoid(view, Objc.sel("setImage:"), image);
        // NSImageOnly = 5 when title is empty (icon-only); NSImageLeading = 2
        // otherwise (icon + title side-by-side).
        boolean titleEmpty = isTitleEmpty();
        Objc.sendVoidLong(view, Objc.sel("setImagePosition:"), titleEmpty ? 5L : 2L);
    }

    private boolean isTitleEmpty() {
        MemorySegment title = Objc.sendPtr(view, Objc.sel("title"));
        if (title == null || title.address() == 0) return true;
        long len = Objc.sendLong(title, Objc.sel("length"));
        return len == 0L;
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
