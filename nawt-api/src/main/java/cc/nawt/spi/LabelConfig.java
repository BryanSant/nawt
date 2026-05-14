package cc.nawt.spi;

/**
 * @param text       label text (non-null; empty if null is passed)
 * @param fontSize   point size; {@code 0} means platform default
 * @param monospace  if {@code true}, render using the platform's monospaced
 *                   system font (digits, code, calculator displays, etc.).
 *                   Independent of {@link #fontSize}.
 */
public record LabelConfig(String text, int fontSize, boolean monospace) {
    public LabelConfig {
        if (text == null) text = "";
        if (fontSize < 0) fontSize = 0;
    }

    public LabelConfig(String text) { this(text, 0, false); }
    public LabelConfig(String text, int fontSize) { this(text, fontSize, false); }
}
