package cc.nawt.backend.macos;

import cc.nawt.spi.DividerConfig;
import cc.nawt.spi.DividerPeer;
import cc.nawt.spi.Orientation;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

/**
 * 1-pt separator line. Backed by NSBox with the "separator" box-type, which
 * AppKit renders using the system-appropriate hairline colour and respects
 * light/dark mode automatically. Sized via Auto Layout: thickness pinned to
 * 1pt; the long axis stretches.
 */
final class MacosDividerPeer implements DividerPeer {

    private final MemorySegment view; // NSBox, retained

    MacosDividerPeer(DividerConfig cfg) {
        MemorySegment v = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSBox")), Objc.sel("init"));
        // NSBoxSeparator = 2 — AppKit renders the hairline separator look.
        Objc.sendVoidLong(v, Objc.sel("setBoxType:"), 2L);
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // Pin the cross-axis dimension to exactly 1pt; long axis is left to
        // the parent container's stretching behaviour.
        boolean horizontal = cfg.orientation() == Orientation.HORIZONTAL;
        String pinDim = horizontal ? "heightAnchor" : "widthAnchor";
        MemorySegment anchor = Objc.sendPtr(v, Objc.sel(pinDim));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(anchor, Objc.sel("constraintEqualToConstant:"), 1.0);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);

        this.view = Objc.sendPtr(v, Objc.sel("retain"));
    }

    MemorySegment view() { return view; }

    @Override public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
