package io.github.swat.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * Minimal Pango FFM bindings. Pango is a hard dependency of GTK 4, so it's
 * already in the address space whenever GTK is loaded — we just open a
 * separate {@link SymbolLookup} on {@code libpango-1.0.so.0} to resolve its
 * symbols without polluting {@link Gtk}.
 *
 * <p>Used by {@link GtkLabelPeer} to apply per-label font sizes via
 * {@code PangoAttrList} ({@code gtk_label_set_attributes}).
 */
final class Pango {
    private Pango() {}

    /** Pango unit scale: a size of {@code N} points equals {@code N * SCALE} in the API's integer size unit. */
    static final int SCALE = 1024;

    private static final SymbolLookup LIB;
    private static final boolean AVAILABLE;

    static {
        SymbolLookup lib;
        boolean ok;
        try {
            lib = SymbolLookup.libraryLookup("libpango-1.0.so.0", Gtk.GLOBAL);
            ok = lib.find("pango_attr_list_new").isPresent();
        } catch (Throwable t) {
            lib = name -> Optional.empty();
            ok = false;
        }
        LIB = lib;
        AVAILABLE = ok;
    }

    static boolean available() { return AVAILABLE; }

    private static MethodHandle bind(String name, FunctionDescriptor fd) {
        return Linker.nativeLinker().downcallHandle(
            LIB.find(name).orElseThrow(() -> new RuntimeException("Missing pango symbol: " + name)),
            fd);
    }

    private static final MethodHandle PANGO_ATTR_LIST_NEW =
        bind("pango_attr_list_new", FunctionDescriptor.of(Gtk.PTR));
    private static final MethodHandle PANGO_ATTR_LIST_UNREF =
        bind("pango_attr_list_unref", FunctionDescriptor.ofVoid(Gtk.PTR));
    private static final MethodHandle PANGO_ATTR_LIST_INSERT =
        bind("pango_attr_list_insert", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));
    private static final MethodHandle PANGO_ATTR_SIZE_NEW =
        bind("pango_attr_size_new", FunctionDescriptor.of(Gtk.PTR, Gtk.INT));
    private static final MethodHandle PANGO_ATTR_FAMILY_NEW =
        bind("pango_attr_family_new", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR));

    static MemorySegment pango_attr_list_new() {
        try { return (MemorySegment) PANGO_ATTR_LIST_NEW.invoke(); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void pango_attr_list_unref(MemorySegment list) {
        try { PANGO_ATTR_LIST_UNREF.invoke(list); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Inserts {@code attr} into {@code list}; the list takes ownership. */
    static void pango_attr_list_insert(MemorySegment list, MemorySegment attr) {
        try { PANGO_ATTR_LIST_INSERT.invoke(list, attr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Build a font-size attribute in Pango units ({@code points * SCALE}). */
    static MemorySegment pango_attr_size_new(int sizeInPangoUnits) {
        try { return (MemorySegment) PANGO_ATTR_SIZE_NEW.invoke(sizeInPangoUnits); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Build a font-family attribute. Generic Pango families: "monospace",
     *  "sans-serif", "serif", "system-ui". */
    static MemorySegment pango_attr_family_new(String family) {
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            return (MemorySegment) PANGO_ATTR_FAMILY_NEW.invoke(arena.allocateFrom(family));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }
}
