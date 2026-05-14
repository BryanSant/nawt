package cc.nawt.backend.macos;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal Objective-C runtime bridge built on FFM. Caches selectors, classes,
 * and {@code objc_msgSend} downcall handles per signature.
 *
 * <p>Frameworks are loaded once on first access; subsequent calls use the
 * already-resolved classes from the loaded image.
 */
public final class Objc {
    private Objc() {}

    static final ValueLayout BOOL = ValueLayout.JAVA_BOOLEAN;
    static final ValueLayout NSINT = ValueLayout.JAVA_LONG;     // NSInteger on 64-bit
    static final ValueLayout NSUINT = ValueLayout.JAVA_LONG;    // NSUInteger on 64-bit
    static final ValueLayout CGFLOAT = ValueLayout.JAVA_DOUBLE; // 64-bit on arm64/x86_64
    static final ValueLayout PTR = ValueLayout.ADDRESS;

    static final Linker LINKER = Linker.nativeLinker();
    static final Arena GLOBAL = Arena.ofShared();

    private static final SymbolLookup LIBOBJC =
        SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", GLOBAL);

    static {
        // Force-load the AppKit and Foundation frameworks so their classes
        // (NSWindow, NSButton, NSString, ...) are registered with the runtime.
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/Foundation.framework/Foundation", GLOBAL);
        SymbolLookup.libraryLookup(
            "/System/Library/Frameworks/AppKit.framework/AppKit", GLOBAL);
    }

    private static final MemorySegment OBJC_MSG_SEND =
        LIBOBJC.find("objc_msgSend").orElseThrow();
    private static final MethodHandle SEL_REGISTER_NAME = LINKER.downcallHandle(
        LIBOBJC.find("sel_registerName").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle OBJC_GET_CLASS = LINKER.downcallHandle(
        LIBOBJC.find("objc_getClass").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle OBJC_ALLOCATE_CLASS_PAIR = LINKER.downcallHandle(
        LIBOBJC.find("objc_allocateClassPair").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR, PTR, ValueLayout.JAVA_LONG));
    private static final MethodHandle OBJC_REGISTER_CLASS_PAIR = LINKER.downcallHandle(
        LIBOBJC.find("objc_registerClassPair").orElseThrow(),
        FunctionDescriptor.ofVoid(PTR));
    private static final MethodHandle CLASS_ADD_METHOD = LINKER.downcallHandle(
        LIBOBJC.find("class_addMethod").orElseThrow(),
        FunctionDescriptor.of(BOOL, PTR, PTR, PTR, PTR));
    private static final MethodHandle CLASS_ADD_PROTOCOL = LINKER.downcallHandle(
        LIBOBJC.find("class_addProtocol").orElseThrow(),
        FunctionDescriptor.of(BOOL, PTR, PTR));
    private static final MethodHandle OBJC_GET_PROTOCOL = LINKER.downcallHandle(
        LIBOBJC.find("objc_getProtocol").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle OBJECT_SET_CLASS = LINKER.downcallHandle(
        LIBOBJC.find("object_setClass").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR, PTR));
    private static final MethodHandle OBJECT_GET_CLASS = LINKER.downcallHandle(
        LIBOBJC.find("object_getClass").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR));
    private static final MethodHandle CLASS_GET_NAME = LINKER.downcallHandle(
        LIBOBJC.find("class_getName").orElseThrow(),
        FunctionDescriptor.of(PTR, PTR));

    private static final ConcurrentHashMap<String, MemorySegment> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, MemorySegment> SEL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<FunctionDescriptor, MethodHandle> SEND_CACHE = new ConcurrentHashMap<>();

    public static final MemorySegment NIL = MemorySegment.NULL;

    /** Look up an Objective-C class by name, e.g. {@code "NSWindow"}. */
    public static MemorySegment cls(String name) {
        return CLASS_CACHE.computeIfAbsent(name, n -> {
            try (var arena = Arena.ofConfined()) {
                MemorySegment cstr = arena.allocateFrom(n);
                MemorySegment c = (MemorySegment) OBJC_GET_CLASS.invoke(cstr);
                if (c == null || c.address() == 0) {
                    throw new RuntimeException("Objective-C class not found: " + n);
                }
                return c;
            } catch (Throwable t) {
                throw new RuntimeException("objc_getClass(" + n + ") failed", t);
            }
        });
    }

    /** Register (or look up) a selector by name, e.g. {@code "alloc"}. */
    public static MemorySegment sel(String name) {
        return SEL_CACHE.computeIfAbsent(name, n -> {
            try (var arena = Arena.ofConfined()) {
                MemorySegment cstr = arena.allocateFrom(n);
                return (MemorySegment) SEL_REGISTER_NAME.invoke(cstr);
            } catch (Throwable t) {
                throw new RuntimeException("sel_registerName(" + n + ") failed", t);
            }
        });
    }

    /** Bind {@code objc_msgSend} for the given signature. Cached. */
    public static MethodHandle msgSend(FunctionDescriptor fd) {
        return SEND_CACHE.computeIfAbsent(fd, f -> LINKER.downcallHandle(OBJC_MSG_SEND, f));
    }

    /** [target alloc] (returns id). */
    public static MemorySegment send_alloc(MemorySegment target) {
        return sendPtr(target, sel("alloc"));
    }

    /** Send a no-arg selector returning id/Class/pointer. */
    public static MemorySegment sendPtr(MemorySegment target, MemorySegment sel) {
        try {
            return (MemorySegment) msgSend(FunctionDescriptor.of(PTR, PTR, PTR)).invoke(target, sel);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a no-arg selector returning void. */
    public static void sendVoid(MemorySegment target, MemorySegment sel) {
        try {
            msgSend(FunctionDescriptor.ofVoid(PTR, PTR)).invoke(target, sel);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a one-arg (pointer) selector returning pointer. */
    public static MemorySegment sendPtr(MemorySegment target, MemorySegment sel, MemorySegment a) {
        try {
            return (MemorySegment) msgSend(FunctionDescriptor.of(PTR, PTR, PTR, PTR)).invoke(target, sel, a);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a one-arg (pointer) selector returning void. */
    public static void sendVoid(MemorySegment target, MemorySegment sel, MemorySegment a) {
        try {
            msgSend(FunctionDescriptor.ofVoid(PTR, PTR, PTR)).invoke(target, sel, a);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a one-arg (long/NSInteger) selector returning void. */
    public static void sendVoidLong(MemorySegment target, MemorySegment sel, long a) {
        try {
            msgSend(FunctionDescriptor.ofVoid(PTR, PTR, NSINT)).invoke(target, sel, a);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a one-arg (boolean) selector returning void. */
    public static void sendVoidBool(MemorySegment target, MemorySegment sel, boolean a) {
        try {
            msgSend(FunctionDescriptor.ofVoid(PTR, PTR, BOOL)).invoke(target, sel, a);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a no-arg selector returning bool. */
    public static boolean sendBool(MemorySegment target, MemorySegment sel) {
        try {
            return (boolean) msgSend(FunctionDescriptor.of(BOOL, PTR, PTR)).invoke(target, sel);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a no-arg selector returning long/NSInteger. */
    public static long sendLong(MemorySegment target, MemorySegment sel) {
        try {
            return (long) msgSend(FunctionDescriptor.of(NSINT, PTR, PTR)).invoke(target, sel);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Send a one-arg (pointer) selector returning long/NSInteger. */
    public static long sendLong(MemorySegment target, MemorySegment sel, MemorySegment a) {
        try {
            return (long) msgSend(FunctionDescriptor.of(NSINT, PTR, PTR, PTR)).invoke(target, sel, a);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Allocate a new Obj-C class pair extending {@code superclass} with the given name. */
    public static MemorySegment allocateClassPair(MemorySegment superclass, String name) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment cstr = arena.allocateFrom(name);
            MemorySegment c = (MemorySegment) OBJC_ALLOCATE_CLASS_PAIR.invoke(superclass, cstr, 0L);
            if (c == null || c.address() == 0) {
                throw new RuntimeException("objc_allocateClassPair(" + name + ") failed (already registered?)");
            }
            return c;
        } catch (Throwable t) { throw asRuntime(t); }
    }

    public static void registerClassPair(MemorySegment cls) {
        try {
            OBJC_REGISTER_CLASS_PAIR.invoke(cls);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Add a method to a (mutable, not-yet-registered) class. {@code typeEncoding} is a c-string like {@code "v@:"}. */
    public static void addMethod(MemorySegment cls, MemorySegment sel, MemorySegment imp, String typeEncoding) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment enc = arena.allocateFrom(typeEncoding);
            boolean ok = (boolean) CLASS_ADD_METHOD.invoke(cls, sel, imp, enc);
            if (!ok) throw new RuntimeException("class_addMethod failed for " + typeEncoding);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Look up a registered Objective-C protocol by name, e.g. {@code "NSDraggingSource"}. */
    public static MemorySegment getProtocol(String name) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment cstr = arena.allocateFrom(name);
            return (MemorySegment) OBJC_GET_PROTOCOL.invoke(cstr);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    /** Declare that {@code cls} adopts {@code protocol}. */
    public static void addProtocol(MemorySegment cls, MemorySegment protocol) {
        if (protocol == null || protocol.address() == 0) return;
        try { CLASS_ADD_PROTOCOL.invoke(cls, protocol); }
        catch (Throwable t) { throw asRuntime(t); }
    }

    /** Swap an object's class (KVO-style isa swizzling). */
    public static void setClass(MemorySegment obj, MemorySegment cls) {
        try { OBJECT_SET_CLASS.invoke(obj, cls); }
        catch (Throwable t) { throw asRuntime(t); }
    }

    /** Get an object's class. */
    public static MemorySegment getClass(MemorySegment obj) {
        try { return (MemorySegment) OBJECT_GET_CLASS.invoke(obj); }
        catch (Throwable t) { throw asRuntime(t); }
    }

    /** Get a class's name as a Java String. */
    public static String getClassName(MemorySegment cls) {
        try {
            MemorySegment cstr = (MemorySegment) CLASS_GET_NAME.invoke(cls);
            if (cstr == null || cstr.address() == 0) return "";
            return cstr.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable t) { throw asRuntime(t); }
    }

    private static RuntimeException asRuntime(Throwable t) {
        if (t instanceof RuntimeException re) return re;
        if (t instanceof Error er) throw er;
        return new RuntimeException(t);
    }
}
