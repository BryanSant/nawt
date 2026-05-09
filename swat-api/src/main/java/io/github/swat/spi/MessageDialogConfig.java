package io.github.swat.spi;

import java.util.List;

/** Configuration passed to {@link PeerFactory#showMessageDialog}. */
public record MessageDialogConfig(
    Style style,
    String title,
    String message,
    String details,
    List<String> buttons,
    int defaultButtonIndex
) {
    public enum Style { INFO, WARNING, ERROR, QUESTION }

    public MessageDialogConfig {
        if (style == null) style = Style.INFO;
        if (title == null) title = "";
        if (message == null) message = "";
        if (details != null && details.isBlank()) details = null;
        if (buttons == null || buttons.isEmpty()) buttons = List.of("OK");
        else buttons = List.copyOf(buttons);
        if (defaultButtonIndex < 0 || defaultButtonIndex >= buttons.size()) defaultButtonIndex = 0;
    }
}
