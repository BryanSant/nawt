package cc.nawt;

import cc.nawt.spi.UiLoop;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Bridge between virtual threads (where app code runs) and the platform UI thread
 * (where every widget call must execute). Delegates to the active backend's
 * {@link UiLoop}.
 */
public final class Ui {
    private Ui() {}

    /** Schedule {@code work} on the UI thread; returns immediately. */
    public static void invokeLater(Runnable work) {
        Toolkit.requireLaunched().uiLoop().invokeLater(work);
    }

    /**
     * Schedule {@code work} on the UI thread, blocking the caller until it
     * completes. Throws if called from the UI thread itself (would deadlock).
     */
    public static <T> T invokeAndWait(Callable<T> work) {
        return Toolkit.requireLaunched().uiLoop().invokeAndWait(work);
    }

    /** Convenience: run {@code work} on the UI thread, blocking. */
    public static void invokeAndWait(Runnable work) {
        invokeAndWait(() -> { work.run(); return null; });
    }

    /** True iff the current thread is the UI thread. */
    public static boolean isUiThread() {
        Toolkit t = Toolkit.tryCurrent();
        return t != null && t.uiLoop().isUiThread();
    }

    /**
     * Run {@code factory} on the UI thread (synchronously, blocking the caller
     * if necessary) and return the result. Used by widget builders to create
     * peers on the correct thread regardless of where the caller runs.
     */
    public static <T> T onUi(Supplier<T> factory) {
        if (isUiThread()) {
            return factory.get();
        }
        return invokeAndWait(factory::get);
    }

    /** Run {@code work} on the UI thread, blocking the caller if needed. */
    public static void runOnUi(Runnable work) {
        if (isUiThread()) {
            work.run();
            return;
        }
        invokeAndWait(work);
    }
}
