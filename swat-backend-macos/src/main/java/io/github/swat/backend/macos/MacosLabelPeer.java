package io.github.swat.backend.macos;

import io.github.swat.spi.LabelConfig;
import io.github.swat.spi.LabelPeer;

import java.lang.foreign.MemorySegment;

final class MacosLabelPeer implements LabelPeer {

    private final MemorySegment view; // NSTextField (label-style), retained

    MacosLabelPeer(LabelConfig config) {
        // [NSTextField labelWithString:nsStr] returns an autoreleased NSTextField
        MemorySegment ns = NSString.from(config.text());
        MemorySegment v = Objc.sendPtr(
            Objc.cls("NSTextField"),
            Objc.sel("labelWithString:"),
            ns);
        this.view = Objc.sendPtr(v, Objc.sel("retain"));
    }

    MemorySegment view() { return view; }

    @Override
    public void setText(String text) {
        Objc.sendVoid(view, Objc.sel("setStringValue:"), NSString.from(text));
    }

    @Override
    public String getText() {
        MemorySegment ns = Objc.sendPtr(view, Objc.sel("stringValue"));
        return NSString.toJava(ns);
    }

    @Override
    public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
