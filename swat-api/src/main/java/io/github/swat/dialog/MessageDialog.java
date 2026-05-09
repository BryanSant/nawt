package io.github.swat.dialog;

import io.github.swat.Toolkit;
import io.github.swat.Ui;
import io.github.swat.spi.MessageDialogConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Modal alert. Build with {@link #builder()}, configure, then call
 * {@link Builder#show()} from a virtual thread; it blocks until the user
 * dismisses the dialog and returns the index (0-based) of the chosen button.
 *
 * <pre>{@code
 * int choice = MessageDialog.builder()
 *     .style(MessageDialog.Style.QUESTION)
 *     .title("Save?")
 *     .message("Save changes before closing?")
 *     .buttons("Save", "Discard", "Cancel")
 *     .show();
 * }</pre>
 */
public final class MessageDialog {

    private MessageDialog() {}

    public enum Style { INFO, WARNING, ERROR, QUESTION }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Style style = Style.INFO;
        private String title = "";
        private String message = "";
        private String details;
        private final List<String> buttons = new ArrayList<>();
        private int defaultButton = 0;

        private Builder() {}

        public Builder style(Style s) { this.style = s; return this; }
        public Builder title(String t) { this.title = t; return this; }
        public Builder message(String m) { this.message = m; return this; }
        public Builder details(String d) { this.details = d; return this; }

        public Builder buttons(String... names) {
            buttons.clear();
            for (String n : names) buttons.add(n);
            return this;
        }

        /** Index (0-based) of the default button — activated when the user presses Enter. */
        public Builder defaultButton(int index) { this.defaultButton = index; return this; }

        /**
         * Show the dialog modally. Returns the index of the button the user
         * clicked. Must NOT be called from the UI thread (would deadlock).
         */
        public int show() {
            if (Ui.isUiThread()) {
                throw new IllegalStateException(
                    "MessageDialog.show() must not be called from the UI thread; use a virtual thread.");
            }
            if (buttons.isEmpty()) buttons.add("OK");
            MessageDialogConfig cfg = new MessageDialogConfig(
                toSpiStyle(style), title, message, details, List.copyOf(buttons), defaultButton);
            CompletableFuture<Integer> result = Ui.onUi(
                () -> Toolkit.requireLaunched().peerFactory().showMessageDialog(cfg));
            try {
                return result.join();
            } catch (Throwable t) {
                Throwable cause = t.getCause() != null ? t.getCause() : t;
                if (cause instanceof RuntimeException re) throw re;
                if (cause instanceof Error er) throw er;
                throw new RuntimeException(cause);
            }
        }
    }

    private static MessageDialogConfig.Style toSpiStyle(Style s) {
        return switch (s) {
            case INFO -> MessageDialogConfig.Style.INFO;
            case WARNING -> MessageDialogConfig.Style.WARNING;
            case ERROR -> MessageDialogConfig.Style.ERROR;
            case QUESTION -> MessageDialogConfig.Style.QUESTION;
        };
    }
}
