package cc.nawt.spi;

/**
 * @param text     button title (non-null; empty if null is passed)
 * @param fontSize point size for the button's title font; {@code 0} means
 *                 "use the platform's default button font"
 */
public record ButtonConfig(String text, int fontSize) {
    public ButtonConfig {
        if (text == null) text = "";
        if (fontSize < 0) fontSize = 0;
    }

    public ButtonConfig(String text) { this(text, 0); }
}
