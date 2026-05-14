package cc.nawt.backend.macos;

import cc.nawt.spi.Orientation;
import cc.nawt.spi.SliderConfig;
import cc.nawt.spi.SliderPeer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.util.function.DoubleConsumer;

final class MacosSliderPeer implements SliderPeer {

    private final MemorySegment view;
    private final MemorySegment target;
    private volatile DoubleConsumer trigger;

    MacosSliderPeer(SliderConfig cfg) {
        this.target = Objc.sendPtr(Delegates.newToggleTarget(), Objc.sel("retain"));

        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSSlider"));
        MemorySegment v = Objc.sendPtr(alloc, Objc.sel("init"));

        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setMinValue:"), cfg.min());
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setMaxValue:"), cfg.max());
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setDoubleValue:"), cfg.initialValue());
        } catch (Throwable t) { throw new RuntimeException(t); }

        if (cfg.orientation() == Orientation.VERTICAL) {
            Objc.sendVoidBool(v, Objc.sel("setVertical:"), true);
        }
        Objc.sendVoidBool(v, Objc.sel("setContinuous:"), true);
        Objc.sendVoid(v, Objc.sel("setTarget:"), target);
        Objc.sendVoid(v, Objc.sel("setAction:"), Delegates.TOGGLE_ACTION_SEL);

        this.view = v;

        Delegates.TOGGLE_HANDLERS.put(target.address(), () -> {
            DoubleConsumer t = trigger;
            if (t != null) {
                try { t.accept(getValue()); }
                catch (Throwable th) { th.printStackTrace(); }
            }
        });
    }

    MemorySegment view() { return view; }

    @Override public void setRange(double min, double max) {
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(view, Objc.sel("setMinValue:"), min);
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(view, Objc.sel("setMaxValue:"), max);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void setValue(double value) {
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(view, Objc.sel("setDoubleValue:"), value);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public double getValue() {
        try {
            return (double) Objc.msgSend(FunctionDescriptor.of(Objc.CGFLOAT, Objc.PTR, Objc.PTR))
                .invoke(view, Objc.sel("doubleValue"));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void onValueChange(DoubleConsumer trigger) { this.trigger = trigger; }

    @Override public void close() {
        Delegates.TOGGLE_HANDLERS.remove(target.address());
        Objc.sendVoid(view, Objc.sel("setTarget:"), Objc.NIL);
        Objc.sendVoid(view, Objc.sel("release"));
        Objc.sendVoid(target, Objc.sel("release"));
    }
}
