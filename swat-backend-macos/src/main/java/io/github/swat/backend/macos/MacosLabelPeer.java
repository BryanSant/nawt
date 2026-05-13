package io.github.swat.backend.macos;

import io.github.swat.spi.LabelConfig;
import io.github.swat.spi.LabelPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class MacosLabelPeer implements LabelPeer {

    /** NSFontWeightRegular — passed to +monospacedSystemFontOfSize:weight: */
    private static final double NS_FONT_WEIGHT_REGULAR = 0.0;

    private final MemorySegment view; // NSTextField (label-style), retained
    private int currentSize;
    private boolean currentMonospace;

    MacosLabelPeer(LabelConfig config) {
        MemorySegment ns = NSString.from(config.text());
        MemorySegment v = Objc.sendPtr(
            Objc.cls("NSTextField"),
            Objc.sel("labelWithString:"),
            ns);
        this.view = Objc.sendPtr(v, Objc.sel("retain"));
        this.currentSize = config.fontSize();
        this.currentMonospace = config.monospace();
        if (currentSize > 0 || currentMonospace) applyFont();
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
    public void setFontSize(int points) {
        this.currentSize = Math.max(0, points);
        applyFont();
    }

    @Override
    public void setMonospace(boolean monospace) {
        this.currentMonospace = monospace;
        applyFont();
    }

    /**
     * Build an NSFont from the current {@link #currentSize} and
     * {@link #currentMonospace} and apply via {@code -setFont:}. Both knobs
     * combine through a single font lookup: monospace selects between
     * {@code +systemFontOfSize:} and {@code +monospacedSystemFontOfSize:weight:},
     * and size 0 in either factory means "platform default."
     */
    private void applyFont() {
        double size = currentSize <= 0 ? 0.0 : (double) currentSize;
        MemorySegment font;
        try {
            if (currentMonospace) {
                font = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT, ValueLayout.JAVA_DOUBLE))
                    .invoke(Objc.cls("NSFont"),
                        Objc.sel("monospacedSystemFontOfSize:weight:"),
                        size, NS_FONT_WEIGHT_REGULAR);
            } else {
                font = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                    .invoke(Objc.cls("NSFont"), Objc.sel("systemFontOfSize:"), size);
            }
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoid(view, Objc.sel("setFont:"), font);
    }

    @Override
    public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
