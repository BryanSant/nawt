package cc.nawt.backend.macos;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Helpers for converting between Java strings and {@code NSString}. The returned
 * NSString from {@link #from(String)} is autoreleased — make sure an
 * {@link AutoreleasePool} is active on the calling thread, or that the result
 * is retained by AppKit (e.g. via {@code [NSWindow setTitle:]}) before the pool
 * drains.
 */
final class NSString {
    private NSString() {}

    /** Java String → autoreleased NSString*. */
    static MemorySegment from(String s) {
        try (var arena = Arena.ofConfined()) {
            MemorySegment cstr = arena.allocateFrom(s == null ? "" : s);
            return Objc.sendPtr(
                Objc.cls("NSString"),
                Objc.sel("stringWithUTF8String:"),
                cstr);
        }
    }

    /** NSString* → Java String. Returns null if the NSString is nil. */
    static String toJava(MemorySegment nsString) {
        if (nsString == null || nsString.address() == 0) return null;
        MemorySegment cstr = Objc.sendPtr(nsString, Objc.sel("UTF8String"));
        if (cstr == null || cstr.address() == 0) return "";
        // The returned char* is owned by the NSString; reinterpret with a long max
        // so we can read until the null terminator.
        return cstr.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
