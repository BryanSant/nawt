package cc.nawt.backend.macos;

import cc.nawt.Painter;
import cc.nawt.spi.CanvasConfig;
import cc.nawt.spi.CanvasPeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.Consumer;

final class MacosCanvasPeer implements CanvasPeer {

    private static final MemoryLayout NSRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);

    private final MemorySegment view;
    private volatile Consumer<Painter> trigger;

    MacosCanvasPeer(CanvasConfig cfg) {
        MemorySegment alloc = Objc.send_alloc(Delegates.CANVAS_VIEW_CLASS);
        // initWithFrame:(NSRect)
        try (var arena = Arena.ofConfined()) {
            MemorySegment frame = arena.allocate(NSRECT);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 0.0);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, (double) cfg.width());
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, (double) cfg.height());
            try {
                this.view = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, NSRECT))
                    .invoke(alloc, Objc.sel("initWithFrame:"), frame);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
        Objc.sendVoidBool(view, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        Delegates.CANVAS_HANDLERS.put(view.address(), ctx -> {
            Consumer<Painter> t = trigger;
            if (t == null) return;
            try { t.accept(new MacosPainter(ctx)); }
            catch (Throwable th) { th.printStackTrace(); }
        });
    }

    MemorySegment view() { return view; }

    @Override public void onPaint(Consumer<Painter> trigger) { this.trigger = trigger; }

    @Override public void invalidate() {
        Objc.sendVoidBool(view, Objc.sel("setNeedsDisplay:"), true);
    }

    @Override public void close() {
        Delegates.CANVAS_HANDLERS.remove(view.address());
        Objc.sendVoid(view, Objc.sel("release"));
    }
}
