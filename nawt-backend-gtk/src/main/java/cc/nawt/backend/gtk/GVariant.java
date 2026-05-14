package cc.nawt.backend.gtk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal GVariant helpers — just enough to build and read the values
 * exchanged with the StatusNotifierWatcher and to satisfy SNI property reads.
 *
 * <p>GLib's {@code g_variant_new} is variadic and not callable from FFM, so
 * we use the typed builder functions ({@code g_variant_new_string},
 * {@code g_variant_new_tuple}, …) instead.
 */
final class GVariant {
    private GVariant() {}

    private static final SymbolLookup LIB =
        SymbolLookup.libraryLookup("libglib-2.0.so.0", Gtk.GLOBAL);

    private static MethodHandle bind(String symbol, FunctionDescriptor fd) {
        return Linker.nativeLinker().downcallHandle(
            LIB.find(symbol).orElseThrow(() ->
                new RuntimeException("Missing GVariant symbol: " + symbol)),
            fd);
    }

    private static final MethodHandle G_VARIANT_NEW_STRING =
        bind("g_variant_new_string", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_VARIANT_NEW_OBJECT_PATH =
        bind("g_variant_new_object_path", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_VARIANT_NEW_BOOLEAN =
        bind("g_variant_new_boolean", FunctionDescriptor.of(Gtk.PTR, Gtk.BOOL));
    private static final MethodHandle G_VARIANT_NEW_TUPLE =
        bind("g_variant_new_tuple", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.LONG));

    private static final MethodHandle G_VARIANT_GET_STRING =
        bind("g_variant_get_string", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_VARIANT_GET_BOOLEAN =
        bind("g_variant_get_boolean", FunctionDescriptor.of(Gtk.BOOL, Gtk.PTR));
    private static final MethodHandle G_VARIANT_GET_CHILD_VALUE =
        bind("g_variant_get_child_value", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.LONG));
    private static final MethodHandle G_VARIANT_REF_SINK =
        bind("g_variant_ref_sink", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_VARIANT_UNREF =
        bind("g_variant_unref", FunctionDescriptor.ofVoid(Gtk.PTR));

    /* ---------- Builders ---------- */

    static MemorySegment newString(String s) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) G_VARIANT_NEW_STRING.invoke(
                arena.allocateFrom(s == null ? "" : s));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment newObjectPath(String path) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) G_VARIANT_NEW_OBJECT_PATH.invoke(
                arena.allocateFrom(path == null ? "/" : path));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment newBoolean(boolean v) {
        try { return (MemorySegment) G_VARIANT_NEW_BOOLEAN.invoke(v ? 1 : 0); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Build a tuple GVariant from {@code children}. The tuple takes
     *  ownership of the children's floating refs. */
    static MemorySegment newTuple(Arena arena, MemorySegment... children) {
        MemorySegment arr = arena.allocate(ValueLayout.ADDRESS, children.length);
        for (int i = 0; i < children.length; i++) {
            arr.setAtIndex(ValueLayout.ADDRESS, i, children[i]);
        }
        try { return (MemorySegment) G_VARIANT_NEW_TUPLE.invoke(arr, (long) children.length); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- Readers ---------- */

    static String getString(MemorySegment variant) {
        if (variant == null || variant.address() == 0) return null;
        try {
            MemorySegment cstr = (MemorySegment) G_VARIANT_GET_STRING.invoke(variant, MemorySegment.NULL);
            if (cstr == null || cstr.address() == 0) return null;
            return cstr.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static boolean getBoolean(MemorySegment variant) {
        try { return ((int) G_VARIANT_GET_BOOLEAN.invoke(variant)) != 0; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment getChildValue(MemorySegment variant, long index) {
        try { return (MemorySegment) G_VARIANT_GET_CHILD_VALUE.invoke(variant, index); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /* ---------- Lifecycle ---------- */

    /** Sink a floating reference and return the sunk variant. Newly-built
     *  GVariants are returned with a floating ref; sinking turns it into a
     *  normal owned ref so we can pass to APIs that consume references. */
    static MemorySegment refSink(MemorySegment variant) {
        try { return (MemorySegment) G_VARIANT_REF_SINK.invoke(variant); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void unref(MemorySegment variant) {
        if (variant == null || variant.address() == 0) return;
        try { G_VARIANT_UNREF.invoke(variant); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
