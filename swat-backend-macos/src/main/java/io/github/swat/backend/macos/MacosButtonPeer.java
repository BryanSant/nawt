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
    public void close() {
        Delegates.BUTTON_HANDLERS.remove(target.address());
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
