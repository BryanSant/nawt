package cc.nawt.backend.gtk;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronous wrapper around {@code gdk_clipboard_read_text_async}.
 * GdkClipboard's only read API is async, so we kick off the read and then
 * spin the main context until the callback fires (or a short deadline elapses).
 *
 * <p>Safe because callers reach this through {@code Toolkit.clipboardText()},
 * which dispatches via {@code Ui.onUi(...)} — the spin runs on the same
 * thread that drives GTK's main loop, so processing the iteration drains the
 * pending async result. Reentrant calls are tolerated via per-call tokens.
 */
final class GtkClipboardRead {
    private GtkClipboardRead() {}

    /** Bounded so a hung clipboard owner doesn't freeze the UI thread forever. */
    private static final long DEADLINE_NS = 500_000_000L; // 500 ms

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1L);
    private static final ConcurrentHashMap<Long, State> STATES = new ConcurrentHashMap<>();
    private static final MemorySegment FINISH_STUB;

    private static final class State {
        volatile boolean done;
        volatile String result;
    }

    static {
        try {
            // void(GObject* source, GAsyncResult* res, gpointer user_data)
            MethodHandle mh = MethodHandles.lookup().findStatic(
                GtkClipboardRead.class, "finishCallback",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            FINISH_STUB = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static String read(MemorySegment clipboard) {
        long token = NEXT_TOKEN.getAndIncrement();
        State state = new State();
        STATES.put(token, state);
        try {
            Gtk.gdk_clipboard_read_text_async(
                clipboard, MemorySegment.NULL, FINISH_STUB, MemorySegment.ofAddress(token));
            long deadline = System.nanoTime() + DEADLINE_NS;
            while (!state.done) {
                Gtk.g_main_context_iteration(MemorySegment.NULL, false);
                if (state.done) break;
                if (System.nanoTime() > deadline) break;
                try { Thread.sleep(1); }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return state.result == null ? "" : state.result;
        } finally {
            STATES.remove(token);
        }
    }

    @SuppressWarnings("unused") // upcalled from GTK
    private static void finishCallback(MemorySegment source, MemorySegment res,
                                       MemorySegment userData) {
        State state = STATES.get(userData.address());
        if (state == null) return;
        try {
            MemorySegment cstr = Gtk.gdk_clipboard_read_text_finish(
                source, res, MemorySegment.NULL);
            if (cstr != null && cstr.address() != 0) {
                state.result = cstr.reinterpret(Long.MAX_VALUE).getString(0);
                Gtk.g_free(cstr);
            } else {
                state.result = "";
            }
        } catch (Throwable t) {
            t.printStackTrace();
            state.result = "";
        } finally {
            state.done = true;
        }
    }
}
