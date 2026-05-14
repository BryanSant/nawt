package cc.nawt.backend.gtk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/** Optional libnotify bindings — silently falls back when not installed. */
final class Notify {
    private Notify() {}

    private static final boolean AVAILABLE;
    private static final MethodHandle NOTIFY_INIT;
    private static final MethodHandle NOTIFY_NOTIFICATION_NEW;
    private static final MethodHandle NOTIFY_NOTIFICATION_SHOW;
    private static volatile boolean initialized;

    static {
        boolean ok;
        MethodHandle init = null, neww = null, show = null;
        try {
            SymbolLookup lib = SymbolLookup.libraryLookup("libnotify.so.4", Gtk.GLOBAL);
            init = bind(lib, "notify_init",
                FunctionDescriptor.of(Gtk.BOOL, Gtk.PTR));
            neww = bind(lib, "notify_notification_new",
                FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));
            show = bind(lib, "notify_notification_show",
                FunctionDescriptor.of(Gtk.BOOL, Gtk.PTR, Gtk.PTR));
            ok = true;
        } catch (Throwable t) {
            ok = false;
        }
        AVAILABLE = ok;
        NOTIFY_INIT = init;
        NOTIFY_NOTIFICATION_NEW = neww;
        NOTIFY_NOTIFICATION_SHOW = show;
    }

    private static MethodHandle bind(SymbolLookup lib, String name, FunctionDescriptor fd) {
        return Linker.nativeLinker().downcallHandle(
            lib.find(name).orElseThrow(() -> new RuntimeException("Missing libnotify symbol: " + name)), fd);
    }

    static void send(String title, String body) {
        if (!AVAILABLE) {
            System.err.println("[nawt] notify: " + title + " — " + body + " (libnotify not available)");
            return;
        }
        try (var arena = Arena.ofConfined()) {
            if (!initialized) {
                NOTIFY_INIT.invoke(arena.allocateFrom("nawt"));
                initialized = true;
            }
            MemorySegment t = arena.allocateFrom(title == null ? "" : title);
            MemorySegment b = arena.allocateFrom(body == null ? "" : body);
            MemorySegment n = (MemorySegment) NOTIFY_NOTIFICATION_NEW.invoke(t, b, MemorySegment.NULL);
            if (n == null || n.address() == 0) return;
            NOTIFY_NOTIFICATION_SHOW.invoke(n, MemorySegment.NULL);
            Gtk.g_object_unref(n);
        } catch (Throwable e) { e.printStackTrace(); }
    }
}
