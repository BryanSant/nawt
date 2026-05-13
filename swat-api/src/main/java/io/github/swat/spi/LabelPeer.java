package io.github.swat.spi;

public non-sealed interface LabelPeer extends Peer {
    void setText(String text);
    String getText();

    /** Set font point size; {@code 0} restores the platform default. */
    void setFontSize(int points);

    /** Toggle monospaced system font on this label. */
    void setMonospace(boolean monospace);
}
