package io.github.swat.spi;

import java.util.concurrent.Callable;

/**
 * Backend-implemented event loop. {@code Toolkit.launch} drives the lifecycle:
 * {@link #bootstrap()} is called on the launcher thread (which becomes the UI
 * thread), then {@link #run()} blocks that thread until {@link #quit()} is
 * invoked.
 */
public interface UiLoop {

    /** Initialize platform state on the calling thread; mark it as the UI thread. */
    void bootstrap();

    /** Block the calling thread running the native event loop until {@link #quit()}. */
    void run();

    /** Ask the loop to exit; safe to call from any thread. */
    void quit();

    /** Schedule {@code work} to run on the UI thread; returns immediately. */
    void invokeLater(Runnable work);

    /**
     * Schedule {@code work} on the UI thread and block until it returns.
     * Throws {@link IllegalStateException} if called from the UI thread.
     */
    <T> T invokeAndWait(Callable<T> work);

    /** True iff the calling thread is the platform UI thread. */
    boolean isUiThread();
}
