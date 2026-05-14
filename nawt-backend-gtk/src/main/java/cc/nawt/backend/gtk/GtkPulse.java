package cc.nawt.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Shared g_timeout callback that pulses a registered GtkProgressBar pointer. */
final class GtkPulse {
    private GtkPulse() {}

    static final MemorySegment STUB;
    private static final AtomicLong NEXT = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, MemorySegment> TARGETS = new ConcurrentHashMap<>();

    static {
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                GtkPulse.class, "tick",
                MethodType.methodType(int.class, MemorySegment.class));
            STUB = Linker.nativeLinker().upcallStub(
                mh, FunctionDescriptor.of(Gtk.BOOL, Gtk.PTR), Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static long register(MemorySegment progressBar) {
        long token = NEXT.getAndIncrement();
        TARGETS.put(token, progressBar);
        return token;
    }

    static void unregister(long token) { TARGETS.remove(token); }

    @SuppressWarnings("unused")
    private static int tick(MemorySegment data) {
        MemorySegment pb = TARGETS.get(data.address());
        if (pb == null) return 0; // remove source
        try { Gtk.gtk_progress_bar_pulse(pb); }
        catch (Throwable t) { t.printStackTrace(); return 0; }
        return 1; // continue
    }
}
