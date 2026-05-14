package cc.nawt;

/**
 * Platform-meaningful capabilities a backend may opt into. Use
 * {@link Toolkit#supports(Capability)} at runtime to branch on what the
 * current host actually offers.
 *
 * <p>NAWT's portable core (Tier 1) does not depend on any of these — it works
 * on every backend. Capabilities cover features that are only meaningful on
 * one or two platforms (translucent backdrops, system tray, global menu bar)
 * or that have a backend-specific implementation behind a portable API
 * (toast overlays, header bars, drag-and-drop).
 */
public enum Capability {
    /** Window header bar — {@code NSToolbar} unified style on macOS,
     *  {@code AdwHeaderBar} on Linux. */
    HEADER_BAR,

    /** Primary "burger" menu rendered inside the header bar (the open-menu-symbolic
     *  {@code GtkMenuButton} pattern used by every modern Adwaita app). Backends
     *  without this idiom — notably macOS, where secondary commands belong in
     *  the global menu bar — leave it unset; samples should fall back to adding
     *  the same commands as a top-level {@code MenuBar} entry. */
    HEADER_BAR_MENU,

    /** Transient in-window toast messages — custom overlay on macOS,
     *  {@code AdwToastOverlay} on Linux. */
    TOAST_OVERLAY,

    /** Drag-and-drop with text payloads. */
    DRAG_TEXT,

    /** macOS-style global menu bar at the top of the screen. The menu bar of
     *  the key window is automatically promoted to {@code NSApp.mainMenu}. */
    GLOBAL_MENU_BAR,

    /** System tray / status icon (e.g. {@code NSStatusItem},
     *  {@code Shell_NotifyIcon}, {@code StatusNotifierItem}). Not yet
     *  implemented on any backend. */
    SYSTEM_TRAY,

    /** Translucent window backdrop ({@code NSVisualEffectView} Vibrancy on
     *  macOS, {@code MicaBackdrop}/{@code DesktopAcrylicBackdrop} on Windows).
     *  Not yet wired through the SPI. */
    TRANSLUCENT_BACKDROP,

    /** Sheet-style modal child windows attached to a parent window. macOS
     *  only via {@code NSWindow beginSheet}. Not yet exposed. */
    SHEET_MODAL_DIALOG,
}
