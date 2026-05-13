package io.github.swat;

import io.github.swat.event.WindowCloseEvent;
import io.github.swat.menu.MenuBar;
import io.github.swat.spi.WindowConfig;
import io.github.swat.spi.WindowPeer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class Window implements Container {

    private final WindowPeer peer;
    private final Widget content;
    private volatile MenuBar menuBar;
    private volatile HeaderBar headerBar;
    private final List<Consumer<WindowCloseEvent>> closeListeners = new CopyOnWriteArrayList<>();
    private volatile boolean shown;
    private volatile boolean closed;

    private Window(WindowPeer peer, Widget content, MenuBar menuBar, HeaderBar headerBar) {
        this.peer = peer;
        this.content = content;
        this.menuBar = menuBar;
        this.headerBar = headerBar;
        peer.onCloseRequest(this::handleCloseRequest);
    }

    public static Builder builder() { return new Builder(); }

    public Widget content() { return content; }

    public Window show() {
        Ui.runOnUi(() -> {
            if (closed) throw new IllegalStateException("Window is already closed");
            if (!shown) {
                Toolkit.requireLaunched().registerWindow(this);
                peer.show();
                shown = true;
            }
        });
        return this;
    }

    public Window title(String title) {
        Ui.runOnUi(() -> peer.setTitle(title));
        return this;
    }

    public Window size(int width, int height) {
        Ui.runOnUi(() -> peer.setSize(width, height));
        return this;
    }

    public Window menuBar(MenuBar bar) {
        Ui.runOnUi(() -> {
            this.menuBar = bar;
            peer.setMenuBar(bar == null ? null : bar.peer());
        });
        return this;
    }

    public MenuBar menuBar() { return menuBar; }

    public Window headerBar(HeaderBar bar) {
        Ui.runOnUi(() -> {
            this.headerBar = bar;
            peer.setHeaderBar(bar == null ? null : bar.peer());
        });
        return this;
    }

    public HeaderBar headerBar() { return headerBar; }

    /** Show a transient in-window message that auto-dismisses after {@code timeoutMs}. */
    public Window toast(String message, int timeoutMs) {
        Ui.runOnUi(() -> peer.toast(message == null ? "" : message,
            timeoutMs <= 0 ? 5000 : timeoutMs));
        return this;
    }

    /** Show a transient toast with a 5-second default timeout. */
    public Window toast(String message) { return toast(message, 5000); }

    /** Listener fires before the window closes. Handlers may call {@link WindowCloseEvent#veto()} to cancel. */
    public Window onClose(Consumer<WindowCloseEvent> handler) {
        closeListeners.add(handler);
        return this;
    }

    private boolean handleCloseRequest() {
        WindowCloseEvent e = new WindowCloseEvent(this);
        for (Consumer<WindowCloseEvent> l : closeListeners) {
            try { l.accept(e); }
            catch (Throwable t) { t.printStackTrace(); }
        }
        if (e.isVetoed()) return false;
        closed = true;
        Toolkit.requireLaunched().unregisterWindow(this);
        return true;
    }

    @Override public Window tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public Window dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public Window acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public WindowPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            if (closed) return;
            if (content != null) content.close();
            if (headerBar != null) headerBar.close();
            peer.close();
            closed = true;
            Toolkit.requireLaunched().unregisterWindow(this);
        });
    }

    public static final class Builder {
        private String title = "";
        private int width = 640;
        private int height = 480;
        private boolean resizable = true;
        private Widget content;
        private MenuBar menuBar;
        private HeaderBar headerBar;

        private Builder() {}

        public Builder title(String title) { this.title = title; return this; }
        public Builder size(int w, int h) { this.width = w; this.height = h; return this; }
        /** Whether the user can resize the window. Default {@code true}. */
        public Builder resizable(boolean resizable) { this.resizable = resizable; return this; }
        public Builder content(Widget content) { this.content = content; return this; }
        public Builder menuBar(MenuBar menuBar) { this.menuBar = menuBar; return this; }
        public Builder headerBar(HeaderBar headerBar) { this.headerBar = headerBar; return this; }

        public Window build() {
            return Ui.onUi(() -> {
                WindowPeer p = Toolkit.requireLaunched().peerFactory()
                    .createWindow(new WindowConfig(title, width, height, resizable));
                // Set header bar before content so the chrome is in place when
                // the content view is resolved against the window's frame.
                if (headerBar != null) p.setHeaderBar(headerBar.peer());
                if (content != null) p.setContent(content.peer());
                if (menuBar != null) p.setMenuBar(menuBar.peer());
                return new Window(p, content, menuBar, headerBar);
            });
        }
    }
}
