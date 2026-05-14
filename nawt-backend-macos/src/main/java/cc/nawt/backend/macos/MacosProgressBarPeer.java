package cc.nawt.backend.macos;

import cc.nawt.spi.ProgressBarConfig;
import cc.nawt.spi.ProgressBarPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

/** NSProgressIndicator in bar style. */
final class MacosProgressBarPeer implements ProgressBarPeer {

    private static final long NS_PROGRESS_INDICATOR_BAR = 0L;

    private final MemorySegment view;
    private boolean indeterminate;

    MacosProgressBarPeer(ProgressBarConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSProgressIndicator"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));
        Objc.sendVoidLong(v, Objc.sel("setStyle:"), NS_PROGRESS_INDICATOR_BAR);
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setMinValue:"), 0.0);
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setMaxValue:"), 1.0);
        } catch (Throwable t) { throw new RuntimeException(t); }
        this.view = v;
        setIndeterminate(cfg.indeterminate());
        if (!cfg.indeterminate()) setValue(cfg.value());
    }

    MemorySegment view() { return view; }

    @Override public void setValue(double value) {
        if (value < 0) value = 0;
        if (value > 1) value = 1;
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(view, Objc.sel("setDoubleValue:"), value);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void setIndeterminate(boolean ind) {
        if (this.indeterminate == ind) return;
        this.indeterminate = ind;
        Objc.sendVoidBool(view, Objc.sel("setIndeterminate:"), ind);
        if (ind) Objc.sendVoid(view, Objc.sel("startAnimation:"), Objc.NIL);
        else     Objc.sendVoid(view, Objc.sel("stopAnimation:"), Objc.NIL);
    }

    @Override public void close() {
        if (indeterminate) Objc.sendVoid(view, Objc.sel("stopAnimation:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
