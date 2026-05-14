package cc.nawt;

import cc.nawt.event.TextChangeEvent;
import cc.nawt.spi.TextFieldConfig;
import cc.nawt.spi.TextFieldPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class TextField implements Widget {

    private final TextFieldPeer peer;
    private final List<Consumer<TextChangeEvent>> asyncListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<TextChangeEvent>> syncListeners = new CopyOnWriteArrayList<>();

    private TextField(TextFieldPeer peer) {
        this.peer = peer;
        peer.onTextChange(this::dispatch);
    }

    public static TextField of() { return of(""); }

    public static TextField of(String initialText) {
        return Ui.onUi(() -> {
            TextFieldPeer p = Toolkit.requireLaunched().peerFactory()
                .createTextField(new TextFieldConfig(initialText));
            return new TextField(p);
        });
    }

    public TextField text(String text) {
        Ui.runOnUi(() -> peer.setText(text));
        return this;
    }

    public String text() {
        return Ui.onUi(peer::getText);
    }

    public TextField onTextChange(Consumer<TextChangeEvent> handler) {
        asyncListeners.add(handler);
        return this;
    }

    public TextField onTextChangeSync(Consumer<TextChangeEvent> handler) {
        syncListeners.add(handler);
        return this;
    }

    private void dispatch(String oldText, String newText) {
        TextChangeEvent e = new TextChangeEvent(this, oldText, newText);
        for (Consumer<TextChangeEvent> l : syncListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        for (Consumer<TextChangeEvent> l : asyncListeners) {
            Thread.startVirtualThread(() -> {
                try { l.accept(e); }
                catch (Throwable t) { t.printStackTrace(); }
            });
        }
    }

    @Override public TextField tooltip(String text) { Widget.super.tooltip(text); return this; }
    @Override public TextField dragText(java.util.function.Supplier<String> textProvider) { Widget.super.dragText(textProvider); return this; }
    @Override public TextField acceptText(java.util.function.Consumer<String> textHandler) { Widget.super.acceptText(textHandler); return this; }

    @Override public TextFieldPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }
}
