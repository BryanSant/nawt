package io.github.swat.backend.gtk;

import io.github.swat.spi.UiLoop;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

final class GtkUiLoop implements UiLoop {

    private static final int G_SOURCE_REMOVE = 0;

    private static final MemorySegment IDLE_DRAIN_STUB;

    static {
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                GtkUiLoop.class, "idleDrain",
                MethodType.methodType(int.class, MemorySegment.class));
            IDLE_DRAIN_STUB = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.of(Gtk.BOOL, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Queue<Runnable> PENDING = new ConcurrentLinkedQueue<>();
    private static final AtomicBoolean SHOULD_EXIT = new AtomicBoolean(false);
    private static volatile Thread UI_THREAD;

    @Override
    public void bootstrap() {
        UI_THREAD = Thread.currentThread();
        Gtk.gtk_init();
    }

    @Override
    public void run() {
        while (!SHOULD_EXIT.get()) {
            Gtk.g_main_context_iteration(MemorySegment.NULL, true);
        }
    }

    @Override
    public void quit() {
        if (SHOULD_EXIT.compareAndSet(false, true)) {
            Gtk.g_main_context_wakeup(MemorySegment.NULL);
        }
    }

    @Override
    public void invokeLater(Runnable work) {
        PENDING.offer(work);
        Gtk.g_idle_add(IDLE_DRAIN_STUB, MemorySegment.NULL);
    }

    @Override
    public <T> T invokeAndWait(Callable<T> work) {
        if (isUiThread()) {
            throw new IllegalStateException(
                "Ui.invokeAndWait must not be called from the UI thread; use invokeLater or call directly.");
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        invokeLater(() -> {
            try { future.complete(work.call()); }
            catch (Throwable t) { future.completeExceptionally(t); }
        });
        try { return future.get(); }
        catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error er) throw er;
            throw new RuntimeException(cause);
        }
    }

    @Override
    public boolean isUiThread() {
        return UI_THREAD != null && Thread.currentThread() == UI_THREAD;
    }

    @SuppressWarnings("unused") // FFM upcall target
    private static int idleDrain(MemorySegment userData) {
        Runnable r;
        while ((r = PENDING.poll()) != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        return G_SOURCE_REMOVE;
    }
}
