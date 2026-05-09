package io.github.swat.spi;

import java.util.function.BooleanSupplier;

public non-sealed interface WindowPeer extends Peer {
    void setTitle(String title);
    void setSize(int width, int height);
    void setContent(Peer content);
    /** Attach a {@link MenuBarPeer}; pass {@code null} to detach. */
    void setMenuBar(MenuBarPeer menuBar);
    /** Attach a {@link HeaderBarPeer}; pass {@code null} to detach. */
    void setHeaderBar(HeaderBarPeer headerBar);
    void show();

    /**
     * Display a transient in-window message. Auto-dismisses after
     * {@code timeoutMs} milliseconds. Implementations:
     * GTK uses {@code AdwToast} on the window's {@code AdwToastOverlay};
     * macOS uses a tinted {@code NSTextField} added to the content view and
     * removed by a Java-side scheduled task.
     */
    void toast(String message, int timeoutMs);

    /**
     * Set the close-request handler. The supplied predicate is invoked when the
     * user attempts to close the window; returning {@code true} permits the close,
     * {@code false} vetoes it. Runs on the UI thread.
     */
    void onCloseRequest(BooleanSupplier permitClose);
}
