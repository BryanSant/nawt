package io.github.swat.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Shared upcall stub for {@code GtkDrawingAreaDrawFunc}:
 * {@code void func(GtkDrawingArea*, cairo_t*, int, int, gpointer)}.
 */
final class GtkDrawCallback {
    private GtkDrawCallback() {}

    static final MemorySegment STUB;
    private static final AtomicLong NEXT = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, Consumer<MemorySegment>> HANDLERS = new ConcurrentHashMap<>();

    static {
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                GtkDrawCallback.class, "draw",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, int.class, int.class, MemorySegment.class));
            STUB = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static long register(Consumer<MemorySegment> handler) {
        long token = NEXT.getAndIncrement();
        HANDLERS.put(token, handler);
        return token;
    }

    static void unregister(long token) { HANDLERS.remove(token); }

    @SuppressWarnings("unused")
    private static void draw(MemorySegment area, MemorySegment cr, int width, int height, MemorySegment data) {
        Consumer<MemorySegment> h = HANDLERS.get(data.address());
        if (h == null) return;
        try { h.accept(cr); }
        catch (Throwable t) { t.printStackTrace(); }
    }
}
