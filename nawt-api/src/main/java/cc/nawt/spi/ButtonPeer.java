package cc.nawt.spi;

public non-sealed interface ButtonPeer extends Peer {
    void setText(String text);

    /**
     * Register a single trigger to be invoked when the button is clicked. The
     * trigger fires on the UI thread; the calling widget façade is responsible
     * for fanning out to user-supplied handlers (sync or virtual-thread).
     */
    void onClick(Runnable trigger);

    /** Set title font point size; {@code 0} restores the platform default. */
    void setFontSize(int points);
}
