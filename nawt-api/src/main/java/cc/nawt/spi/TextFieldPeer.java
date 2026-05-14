package cc.nawt.spi;

import java.util.function.BiConsumer;

public non-sealed interface TextFieldPeer extends Peer {
    void setText(String text);
    String getText();

    /**
     * Register a trigger fired on the UI thread when the text changes.
     * Arguments are (oldText, newText).
     */
    void onTextChange(BiConsumer<String, String> trigger);
}
