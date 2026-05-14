package cc.nawt.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Bridges GTK signal callbacks to Java. Allocates one upcall stub per signal
 * shape and passes a Java-managed token (cast to gpointer) as the user_data
 * so we can dispatch to the right Runnable / BooleanSupplier on each fire.
 */
final class GtkSignals {
    private GtkSignals() {}

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1L);
    private static final ConcurrentHashMap<Long, Runnable> VOID_HANDLERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, BooleanSupplier> BOOL_HANDLERS = new ConcurrentHashMap<>();

    private static final MemorySegment VOID_STUB;
    private static final MemorySegment BOOL_STUB;
    private static final MemorySegment VOID3_STUB;

    static {
        try {
            MethodHandle voidMh = MethodHandles.lookup().findStatic(
                GtkSignals.class, "voidCallback",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class));
            VOID_STUB = Linker.nativeLinker().upcallStub(
                voidMh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);

            MethodHandle boolMh = MethodHandles.lookup().findStatic(
                GtkSignals.class, "boolCallback",
                MethodType.methodType(int.class, MemorySegment.class, MemorySegment.class));
            BOOL_STUB = Linker.nativeLinker().upcallStub(
                boolMh,
                FunctionDescriptor.of(Gtk.BOOL, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);

            MethodHandle void3Mh = MethodHandles.lookup().findStatic(
                GtkSignals.class, "void3Callback",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            VOID3_STUB = Linker.nativeLinker().upcallStub(
                void3Mh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /** Connect a {@code void(self, user_data)}-shaped signal handler. */
    static long connectVoid(MemorySegment instance, String signal, Runnable handler) {
        long token = NEXT_TOKEN.getAndIncrement();
        VOID_HANDLERS.put(token, handler);
        MemorySegment data = MemorySegment.ofAddress(token);
        return Gtk.g_signal_connect_data(
            instance, signal, VOID_STUB, data, MemorySegment.NULL, 0);
    }

    /** Connect a {@code gboolean(self, user_data)}-shaped signal handler (e.g. {@code close-request}). */
    static long connectBool(MemorySegment instance, String signal, BooleanSupplier handler) {
        long token = NEXT_TOKEN.getAndIncrement();
        BOOL_HANDLERS.put(token, handler);
        MemorySegment data = MemorySegment.ofAddress(token);
        return Gtk.g_signal_connect_data(
            instance, signal, BOOL_STUB, data, MemorySegment.NULL, 0);
    }

    /** Connect a {@code void(self, arg, user_data)}-shaped handler (e.g. GAction {@code activate}). */
    static long connectVoid3(MemorySegment instance, String signal, Runnable handler) {
        long token = NEXT_TOKEN.getAndIncrement();
        VOID_HANDLERS.put(token, handler);
        MemorySegment data = MemorySegment.ofAddress(token);
        return Gtk.g_signal_connect_data(
            instance, signal, VOID3_STUB, data, MemorySegment.NULL, 0);
    }

    @SuppressWarnings("unused")
    private static void voidCallback(MemorySegment self, MemorySegment userData) {
        Runnable r = VOID_HANDLERS.get(userData.address());
        if (r == null) return;
        try { r.run(); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    @SuppressWarnings("unused")
    private static int boolCallback(MemorySegment self, MemorySegment userData) {
        BooleanSupplier h = BOOL_HANDLERS.get(userData.address());
        if (h == null) return 0; // FALSE — let default action proceed
        try { return h.getAsBoolean() ? 1 : 0; }
        catch (Throwable t) { t.printStackTrace(); return 0; }
    }

    @SuppressWarnings("unused")
    private static void void3Callback(MemorySegment self, MemorySegment arg, MemorySegment userData) {
        Runnable r = VOID_HANDLERS.get(userData.address());
        if (r == null) return;
        try { r.run(); }
        catch (Throwable t) { t.printStackTrace(); }
    }
}
