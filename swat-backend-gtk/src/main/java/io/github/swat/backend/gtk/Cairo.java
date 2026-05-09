package io.github.swat.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/** Minimal Cairo FFM bindings used by the Canvas backend. */
final class Cairo {
    private Cairo() {}

    private static final SymbolLookup LIB;
    private static final boolean AVAILABLE;

    static {
        SymbolLookup lib;
        boolean ok;
        try {
            lib = SymbolLookup.libraryLookup("libcairo.so.2", Gtk.GLOBAL);
            ok = lib.find("cairo_set_source_rgba").isPresent();
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
            LIB.find(name).orElseThrow(() -> new RuntimeException("Missing cairo symbol: " + name)),
            fd);
    }

    private static final MethodHandle CAIRO_SET_SOURCE_RGBA = bind(
        "cairo_set_source_rgba",
        FunctionDescriptor.ofVoid(Gtk.PTR,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle CAIRO_RECTANGLE = bind(
        "cairo_rectangle",
        FunctionDescriptor.ofVoid(Gtk.PTR,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle CAIRO_FILL = bind(
        "cairo_fill", FunctionDescriptor.ofVoid(Gtk.PTR));
    private static final MethodHandle CAIRO_STROKE = bind(
        "cairo_stroke", FunctionDescriptor.ofVoid(Gtk.PTR));
    private static final MethodHandle CAIRO_MOVE_TO = bind(
        "cairo_move_to",
        FunctionDescriptor.ofVoid(Gtk.PTR, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle CAIRO_LINE_TO = bind(
        "cairo_line_to",
        FunctionDescriptor.ofVoid(Gtk.PTR, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE));
    private static final MethodHandle CAIRO_NEW_PATH = bind(
        "cairo_new_path", FunctionDescriptor.ofVoid(Gtk.PTR));

    static void setSourceRGBA(MemorySegment cr, double r, double g, double b, double a) {
        try { CAIRO_SET_SOURCE_RGBA.invoke(cr, r, g, b, a); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void rectangle(MemorySegment cr, double x, double y, double w, double h) {
        try { CAIRO_RECTANGLE.invoke(cr, x, y, w, h); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void fill(MemorySegment cr) {
        try { CAIRO_FILL.invoke(cr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void stroke(MemorySegment cr) {
        try { CAIRO_STROKE.invoke(cr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void moveTo(MemorySegment cr, double x, double y) {
        try { CAIRO_MOVE_TO.invoke(cr, x, y); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void lineTo(MemorySegment cr, double x, double y) {
        try { CAIRO_LINE_TO.invoke(cr, x, y); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void newPath(MemorySegment cr) {
        try { CAIRO_NEW_PATH.invoke(cr); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
