package cc.nawt.spi;

import cc.nawt.LabelStyle;

/**
 * @param text       label text (non-null; empty if null is passed)
 * @param fontSize   point size; {@code 0} means platform default
 * @param monospace  if {@code true}, render using the platform's monospaced
 *                   system font (digits, code, calculator displays, etc.).
 *                   Independent of {@link #fontSize}.
 * @param style      semantic colour role; defaults to {@link LabelStyle#PRIMARY}
 */
public record LabelConfig(String text, int fontSize, boolean monospace, LabelStyle style) {
    public LabelConfig {
        if (text == null) text = "";
        if (fontSize < 0) fontSize = 0;
        if (style == null) style = LabelStyle.PRIMARY;
    }

    public LabelConfig(String text) { this(text, 0, false, LabelStyle.PRIMARY); }
    public LabelConfig(String text, int fontSize) { this(text, fontSize, false, LabelStyle.PRIMARY); }
    public LabelConfig(String text, int fontSize, boolean monospace) {
        this(text, fontSize, monospace, LabelStyle.PRIMARY);
    }
}
