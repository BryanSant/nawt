package io.github.swat.backend.macos;

import io.github.swat.spi.TextFieldConfig;
import io.github.swat.spi.TextFieldPeer;

import java.lang.foreign.MemorySegment;
import java.util.function.BiConsumer;

final class MacosTextFieldPeer implements TextFieldPeer {

    private final MemorySegment view;     // NSTextField (editable), retained
    private final MemorySegment delegate; // SwatTextFieldDelegate, retained
    private volatile String lastText;
    private volatile BiConsumer<String, String> trigger;

    MacosTextFieldPeer(TextFieldConfig config) {
        this.lastText = config.initialText() == null ? "" : config.initialText();
        // +[NSTextField textFieldWithString:]
        MemorySegment v = Objc.sendPtr(
            Objc.cls("NSTextField"),
            Objc.sel("textFieldWithString:"),
            NSString.from(this.lastText));
        this.view = Objc.sendPtr(v, Objc.sel("retain"));

        this.delegate = Objc.sendPtr(Delegates.newTextFieldDelegate(), Objc.sel("retain"));
        Objc.sendVoid(view, Objc.sel("setDelegate:"), delegate);

        Delegates.TEXTFIELD_HANDLERS.put(delegate.address(), this::fireChange);
    }

    MemorySegment view() { return view; }

    private void fireChange() {
        BiConsumer<String, String> t = trigger;
        if (t == null) return;
        String oldText = lastText;
        String newText = readText();
        lastText = newText;
        try { t.accept(oldText, newText); }
        catch (Throwable th) { th.printStackTrace(); }
    }

    private String readText() {
        MemorySegment ns = Objc.sendPtr(view, Objc.sel("stringValue"));
        String s = NSString.toJava(ns);
        return s == null ? "" : s;
    }

    @Override
    public void setText(String text) {
        String newText = text == null ? "" : text;
        Objc.sendVoid(view, Objc.sel("setStringValue:"), NSString.from(newText));
        lastText = newText;
    }

    @Override
    public String getText() {
        return readText();
    }

    @Override
    public void onTextChange(BiConsumer<String, String> trigger) {
        this.trigger = trigger;
    }

    @Override
    public void close() {
        Delegates.TEXTFIELD_HANDLERS.remove(delegate.address());
        Objc.sendVoid(view, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(delegate, Objc.sel("release"));
    }
}
