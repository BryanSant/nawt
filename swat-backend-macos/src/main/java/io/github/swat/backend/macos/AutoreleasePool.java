package io.github.swat.backend.macos;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Try-with-resources wrapper around {@code objc_autoreleasePoolPush} /
 * {@code objc_autoreleasePoolPop}. Use around any code that creates
 * autoreleased Objective-C objects (e.g. {@code stringWithUTF8String:}).
 */
final class AutoreleasePool implements AutoCloseable {

    private static final MethodHandle PUSH;
    private static final MethodHandle POP;

    static {
        SymbolLookup obj = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Objc.GLOBAL);
        PUSH = Objc.LINKER.downcallHandle(
            obj.find("objc_autoreleasePoolPush").orElseThrow(),
            FunctionDescriptor.of(Objc.PTR));
        POP = Objc.LINKER.downcallHandle(
            obj.find("objc_autoreleasePoolPop").orElseThrow(),
            FunctionDescriptor.ofVoid(Objc.PTR));
    }

    private final MemorySegment token;

    private AutoreleasePool(MemorySegment token) { this.token = token; }

    static AutoreleasePool push() {
        try {
            return new AutoreleasePool((MemorySegment) PUSH.invoke());
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override public void close() {
        try { POP.invoke(token); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
