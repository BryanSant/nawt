package io.github.swat;

import io.github.swat.menu.Menu;
import io.github.swat.spi.SystemTrayConfig;
import io.github.swat.spi.SystemTrayPeer;

/**
 * System tray / status icon. Backed by {@code NSStatusItem} on macOS and
 * {@code StatusNotifierItem} on Linux (when {@link Capability#SYSTEM_TRAY}
 * is supported). Multiple tray instances per application are permitted.
 *
 * <p>Tray support is opt-in per backend — gate creation on
 * {@link Toolkit#supports(Capability) Toolkit.supports(Capability.SYSTEM_TRAY)}
 * to fail gracefully on hosts that don't have it.
 */
public final class SystemTray implements AutoCloseable {

    private final SystemTrayPeer peer;
    private volatile Menu menu;

    private SystemTray(SystemTrayPeer peer) { this.peer = peer; }

    public static Builder builder() { return new Builder(); }

    /** Quick-start: just an icon and tooltip, no menu. */
    public static SystemTray of(String iconPath, String tooltip) {
        return builder().iconPath(iconPath).tooltip(tooltip).build();
    }

    public SystemTray iconPath(String path) {
        Ui.runOnUi(() -> peer.setIconPath(path));
        return this;
    }

    public SystemTray tooltip(String text) {
        Ui.runOnUi(() -> peer.setTooltip(text));
        return this;
    }

    public SystemTray menu(Menu menu) {
        Ui.runOnUi(() -> {
            this.menu = menu;
            peer.setMenu(menu == null ? null : menu.peer());
        });
        return this;
    }

    public Menu menu() { return menu; }

    public SystemTrayPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(peer::close);
    }

    public static final class Builder {
        private String iconPath;
        private String tooltip;
        private Menu menu;

        private Builder() {}

        public Builder iconPath(String path) { this.iconPath = path; return this; }
        public Builder tooltip(String text) { this.tooltip = text; return this; }
        public Builder menu(Menu menu) { this.menu = menu; return this; }

        public SystemTray build() {
            return Ui.onUi(() -> {
                SystemTrayPeer p = Toolkit.requireLaunched().peerFactory()
                    .createSystemTray(new SystemTrayConfig(
                        iconPath, tooltip, menu == null ? null : menu.peer()));
                SystemTray tray = new SystemTray(p);
                tray.menu = menu;
                return tray;
            });
        }
    }
}
