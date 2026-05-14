package cc.nawt.spi;

import cc.nawt.SystemIcon;

/**
 * @param text     button title (non-null; empty if null is passed)
 * @param fontSize point size for the button's title font; {@code 0} means
 *                 "use the platform's default button font"
 * @param icon     optional system icon shown beside (or in place of) the
 *                 title; {@code null} means no icon
 */
public record ButtonConfig(String text, int fontSize, SystemIcon icon) {
    public ButtonConfig {
        if (text == null) text = "";
        if (fontSize < 0) fontSize = 0;
    }

    public ButtonConfig(String text) { this(text, 0, null); }
    public ButtonConfig(String text, int fontSize) { this(text, fontSize, null); }
}
