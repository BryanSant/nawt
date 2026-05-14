package cc.nawt.backend.macos;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** Minimal CoreGraphics FFM bindings used by the Canvas backend. */
final class CG {
    private CG() {}

    static final MemoryLayout CGRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,  // origin
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE); // size

    private static final SymbolLookup LIB = SymbolLookup.libraryLookup(
        "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", Objc.GLOBAL);

    private static MethodHandle bind(String name, FunctionDescriptor fd) {
        return Objc.LINKER.downcallHandle(LIB.find(name).orElseThrow(), fd);
    }

    private static final MethodHandle CG_CTX_SET_RGB_FILL = bind(
        "CGContextSetRGBFillColor",
        FunctionDescriptor.ofVoid(Objc.PTR, Objc.CGFLOAT, Objc.CGFLOAT, Objc.CGFLOAT, Objc.CGFLOAT));
    private static final MethodHandle CG_CTX_SET_RGB_STROKE = bind(
        "CGContextSetRGBStrokeColor",
        FunctionDescriptor.ofVoid(Objc.PTR, Objc.CGFLOAT, Objc.CGFLOAT, Objc.CGFLOAT, Objc.CGFLOAT));
    private static final MethodHandle CG_CTX_FILL_RECT = bind(
        "CGContextFillRect",
        FunctionDescriptor.ofVoid(Objc.PTR, CGRECT));
    private static final MethodHandle CG_CTX_STROKE_RECT = bind(
        "CGContextStrokeRect",
        FunctionDescriptor.ofVoid(Objc.PTR, CGRECT));
    private static final MethodHandle CG_CTX_MOVE_TO = bind(
        "CGContextMoveToPoint",
        FunctionDescriptor.ofVoid(Objc.PTR, Objc.CGFLOAT, Objc.CGFLOAT));
    private static final MethodHandle CG_CTX_ADD_LINE = bind(
        "CGContextAddLineToPoint",
        FunctionDescriptor.ofVoid(Objc.PTR, Objc.CGFLOAT, Objc.CGFLOAT));
    private static final MethodHandle CG_CTX_STROKE_PATH = bind(
        "CGContextStrokePath",
        FunctionDescriptor.ofVoid(Objc.PTR));
    private static final MethodHandle CG_CTX_BEGIN_PATH = bind(
        "CGContextBeginPath",
        FunctionDescriptor.ofVoid(Objc.PTR));

    static void setFillRGBA(MemorySegment ctx, double r, double g, double b, double a) {
        try { CG_CTX_SET_RGB_FILL.invoke(ctx, r, g, b, a); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void setStrokeRGBA(MemorySegment ctx, double r, double g, double b, double a) {
        try { CG_CTX_SET_RGB_STROKE.invoke(ctx, r, g, b, a); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void fillRect(MemorySegment ctx, MemorySegment rect) {
        try { CG_CTX_FILL_RECT.invoke(ctx, rect); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void strokeRect(MemorySegment ctx, MemorySegment rect) {
        try { CG_CTX_STROKE_RECT.invoke(ctx, rect); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void moveTo(MemorySegment ctx, double x, double y) {
        try { CG_CTX_MOVE_TO.invoke(ctx, x, y); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void addLineTo(MemorySegment ctx, double x, double y) {
        try { CG_CTX_ADD_LINE.invoke(ctx, x, y); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void beginPath(MemorySegment ctx) {
        try { CG_CTX_BEGIN_PATH.invoke(ctx); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void strokePath(MemorySegment ctx) {
        try { CG_CTX_STROKE_PATH.invoke(ctx); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
