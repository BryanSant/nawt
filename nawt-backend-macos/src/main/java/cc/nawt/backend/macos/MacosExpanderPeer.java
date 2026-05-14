package cc.nawt.backend.macos;

import cc.nawt.spi.ExpanderConfig;
import cc.nawt.spi.ExpanderPeer;
import cc.nawt.spi.Peer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

/**
 * AppKit doesn't ship a single Expander control; the idiomatic shape is a
 * disclosure triangle button (NSButton with NSButtonTypeOnOff + bezel
 * NSDisclosureBezelStyle) above a content view we hide/show. Wrapped in a
 * vertical NSStackView so layout collapses when the content is hidden.
 */
final class MacosExpanderPeer implements ExpanderPeer {

    private static final long NS_BEZEL_DISCLOSURE = 5L;
    private static final long NS_BUTTON_TYPE_ONOFF = 6L;

    private final MemorySegment stack;
    private final MemorySegment headerRow;
    private final MemorySegment titleLabel;
    private final MemorySegment disclosure;
    private final MemorySegment target;
    private MemorySegment content;
    private boolean expanded;
    private volatile Consumer<Boolean> trigger;

    MacosExpanderPeer(ExpanderConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newToggleTarget(), Objc.sel("retain"));

        MemorySegment stackAlloc = Objc.send_alloc(Objc.cls("NSStackView"));
        this.stack = Objc.sendPtr(stackAlloc, Objc.sel("init"));
        Objc.sendVoidLong(stack, Objc.sel("setOrientation:"), 1L); // vertical
        Objc.sendVoidLong(stack, Objc.sel("setAlignment:"), 5L); // NSLayoutAttributeLeading
        Objc.sendVoidLong(stack, Objc.sel("setDistribution:"), 0L); // Fill
        Objc.sendVoidBool(stack, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);
        // Expander itself should stretch horizontally so the disclosure header
        // and content span the parent's cross axis.
        MacosContainerPeer.setContentHuggingPriority(stack, 1, 0L);
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(stack, Objc.sel("setSpacing:"), 4.0);
        } catch (Throwable t) { throw new RuntimeException(t); }

        MemorySegment rowAlloc = Objc.send_alloc(Objc.cls("NSStackView"));
        this.headerRow = Objc.sendPtr(rowAlloc, Objc.sel("init"));
        Objc.sendVoidLong(headerRow, Objc.sel("setOrientation:"), 0L); // horizontal
        Objc.sendVoidLong(headerRow, Objc.sel("setAlignment:"), 3L); // NSLayoutAttributeTop
        Objc.sendVoidLong(headerRow, Objc.sel("setDistribution:"), 0L); // Fill
        Objc.sendVoidBool(headerRow, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        MemorySegment btnAlloc = Objc.send_alloc(Objc.cls("NSButton"));
        this.disclosure = Objc.sendPtr(btnAlloc, Objc.sel("init"));
        Objc.sendVoidLong(disclosure, Objc.sel("setBezelStyle:"), NS_BEZEL_DISCLOSURE);
        Objc.sendVoidLong(disclosure, Objc.sel("setButtonType:"), NS_BUTTON_TYPE_ONOFF);
        Objc.sendVoid(disclosure, Objc.sel("setTitle:"), NSString.from(""));
        Objc.sendVoid(disclosure, Objc.sel("setTarget:"), target);
        Objc.sendVoid(disclosure, Objc.sel("setAction:"), Delegates.TOGGLE_ACTION_SEL);
        Objc.sendVoidLong(disclosure, Objc.sel("setState:"), cfg.initialExpanded() ? 1L : 0L);

        MemorySegment lbl = Objc.sendPtr(
            Objc.cls("NSTextField"), Objc.sel("labelWithString:"), NSString.from(cfg.title()));
        this.titleLabel = Objc.sendPtr(lbl, Objc.sel("retain"));

        // Header row is horizontal: pin children's heightAnchor to row.
        MacosContainerPeer.addArrangedFillingCrossAxis(headerRow, disclosure, false, 0);
        MacosContainerPeer.addArrangedFillingCrossAxis(headerRow, titleLabel, false, 0);
        // Outer stack is vertical: pin children's widthAnchor to stack.
        MacosContainerPeer.addArrangedFillingCrossAxis(stack, headerRow, true, 0);

        this.expanded = cfg.initialExpanded();

        Delegates.TOGGLE_HANDLERS.put(target.address(), () -> {
            boolean now = Objc.sendLong(disclosure, Objc.sel("state")) != 0;
            if (now == expanded) return;
            expanded = now;
            updateContentVisibility();
            Consumer<Boolean> t = trigger;
            if (t != null) {
                try { t.accept(now); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment view() { return stack; }

    private void updateContentVisibility() {
        if (content == null || content.address() == 0) return;
        Objc.sendVoidBool(content, Objc.sel("setHidden:"), !expanded);
    }

    @Override public void setTitle(String title) {
        Objc.sendVoid(titleLabel, Objc.sel("setStringValue:"), NSString.from(title));
    }

    @Override public void setContent(Peer child) {
        MemorySegment cv = MacosContainerPeer.peerView(child);
        if (this.content != null && this.content.address() != 0) {
            Objc.sendVoid(this.content, Objc.sel("removeFromSuperview"));
        }
        this.content = cv;
        MacosContainerPeer.addArrangedFillingCrossAxis(stack, cv, true, 0);
        updateContentVisibility();
    }

    @Override public void setExpanded(boolean on) {
        if (on == expanded) return;
        expanded = on;
        Objc.sendVoidLong(disclosure, Objc.sel("setState:"), on ? 1L : 0L);
        updateContentVisibility();
    }

    @Override public boolean isExpanded() { return expanded; }

    @Override public void onExpandedChange(Consumer<Boolean> trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.TOGGLE_HANDLERS.remove(target.address());
        Objc.sendVoid(disclosure, Objc.sel("setTarget:"), Objc.NIL);
        Objc.sendVoid(stack, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
        Objc.sendVoid(titleLabel, Objc.sel("release"));
    }
}
