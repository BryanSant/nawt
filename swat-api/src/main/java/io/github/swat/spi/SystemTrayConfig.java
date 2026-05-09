package io.github.swat.spi;

/**
 * Configuration for a system tray / status icon.
 *
 * @param iconPath  filesystem path to a template image (typically a monochrome
 *                  PNG; macOS will tint it for dark mode), or {@code null} for
 *                  no icon
 * @param tooltip   hover tooltip on the tray icon, or {@code null}
 * @param menu      menu peer to attach. On Linux this is mandatory at
 *                  construction time because the SNI registration includes the
 *                  exported menu's object path; on macOS it can be {@code null}
 *                  and set later via {@code setMenu}
 */
public record SystemTrayConfig(String iconPath, String tooltip, MenuPeer menu) {
    public SystemTrayConfig {
        if (iconPath != null && iconPath.isBlank()) iconPath = null;
        if (tooltip != null && tooltip.isBlank()) tooltip = null;
    }

    /** Backward-compatible two-arg constructor — equivalent to a null menu. */
    public SystemTrayConfig(String iconPath, String tooltip) {
        this(iconPath, tooltip, null);
    }
}
