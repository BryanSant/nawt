package cc.nawt.spi;

import cc.nawt.LabelStyle;

public non-sealed interface LabelPeer extends Peer {
    void setText(String text);
    String getText();

    /** Set font point size; {@code 0} restores the platform default. */
    void setFontSize(int points);

    /** Toggle monospaced system font on this label. */
    void setMonospace(boolean monospace);

    /** Apply a semantic colour role. {@code null} resets to PRIMARY. */
    void setStyle(LabelStyle style);
}
