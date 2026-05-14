package cc.nawt;

import cc.nawt.spi.NavigationSplitConfig;
import cc.nawt.spi.NavigationSplitPeer;

/**
 * A two-pane container styled as a sidebar-and-detail navigation split.
 * Unlike the generic {@link Splitter}, this widget gets host-native split
 * chrome: vibrant translucent sidebar background, titlebar extension, and
 * (on macOS) a "toggle sidebar" toolbar item when the parent window has a
 * {@link HeaderBar}.
 *
 * <p>Requires {@link Capability#NAVIGATION_SPLIT} on the active backend.
 */
public final class NavigationSplit implements Container {

    private final NavigationSplitPeer peer;
    private final Widget sidebar;
    private final Widget detail;

    private NavigationSplit(NavigationSplitPeer peer, Widget sidebar, Widget detail) {
        this.peer = peer;
        this.sidebar = sidebar;
        this.detail = detail;
    }

    public static NavigationSplit of(Widget sidebar, Widget detail) {
        return builder().sidebar(sidebar).detail(detail).build();
    }

    public static Builder builder() { return new Builder(); }

    public Widget sidebar() { return sidebar; }
    public Widget detail() { return detail; }

    /** Replace the detail-pane widget. The old detail's lifecycle stays with the caller. */
    public NavigationSplit detail(Widget newDetail) {
        Ui.runOnUi(() -> peer.setDetail(newDetail == null ? null : newDetail.peer()));
        return this;
    }

    @Override public NavigationSplit tooltip(String text) { Container.super.tooltip(text); return this; }
    @Override public NavigationSplit dragText(java.util.function.Supplier<String> textProvider) { Container.super.dragText(textProvider); return this; }
    @Override public NavigationSplit acceptText(java.util.function.Consumer<String> textHandler) { Container.super.acceptText(textHandler); return this; }

    @Override public NavigationSplitPeer peer() { return peer; }

    @Override public void close() {
        Ui.runOnUi(() -> {
            if (sidebar != null) sidebar.close();
            if (detail != null) detail.close();
            peer.close();
        });
    }

    public static final class Builder {
        private Widget sidebar;
        private Widget detail;
        private int preferredWidth;
        private int minWidth;

        private Builder() {}

        public Builder sidebar(Widget sidebar) { this.sidebar = sidebar; return this; }
        public Builder detail(Widget detail) { this.detail = detail; return this; }

        /** Preferred width of the sidebar pane in points. {@code 0} uses the host default. */
        public Builder sidebarWidth(int points) { this.preferredWidth = Math.max(0, points); return this; }

        /** Minimum width of the sidebar pane in points. {@code 0} uses the host default. */
        public Builder sidebarMinWidth(int points) { this.minWidth = Math.max(0, points); return this; }

        public NavigationSplit build() {
            if (sidebar == null) throw new IllegalStateException("sidebar widget is required");
            if (detail == null)  throw new IllegalStateException("detail widget is required");
            Widget sb = sidebar;
            Widget dt = detail;
            int pref = preferredWidth;
            int min = minWidth;
            return Ui.onUi(() -> {
                NavigationSplitPeer p = Toolkit.requireLaunched().peerFactory()
                    .createNavigationSplit(new NavigationSplitConfig(sb.peer(), dt.peer(), pref, min));
                return new NavigationSplit(p, sb, dt);
            });
        }
    }
}
