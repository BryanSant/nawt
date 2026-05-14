package cc.nawt.spi;

public interface MenuBarPeer extends AutoCloseable {
    /** Append a top-level menu (e.g. {@code File}, {@code Edit}). */
    void addMenu(MenuPeer menu);

    @Override
    void close();
}
