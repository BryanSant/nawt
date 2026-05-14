package cc.nawt;

/**
 * A platform-neutral handle for a common UI glyph. Each enum value carries the
 * host-native identifiers needed to resolve the icon at peer-creation time —
 * SF Symbols on macOS, the GTK/freedesktop icon-theme name on Linux. Backends
 * without an equivalent native icon set should leave
 * {@link Capability#SYSTEM_ICONS} unset and refuse the API.
 *
 * <p>The deliberately small starter set covers the icons NAWT samples actually
 * use today (Landmarks, Demo). Add new values when a real use case appears —
 * not preemptively.
 */
public enum SystemIcon {
    HEART("heart", "emblem-favorite-symbolic"),
    HEART_FILL("heart.fill", "emblem-favorite-symbolic"),
    STAR("star", "starred-symbolic"),
    STAR_FILL("star.fill", "starred-symbolic"),
    MAGNIFYING_GLASS("magnifyingglass", "system-search-symbolic"),
    INFO("info.circle", "dialog-information-symbolic"),
    INFO_FILL("info.circle.fill", "dialog-information-symbolic"),
    ELLIPSIS("ellipsis", "view-more-symbolic"),
    GEAR("gearshape", "preferences-system-symbolic"),
    CHEVRON_LEFT("chevron.left", "go-previous-symbolic"),
    CHEVRON_RIGHT("chevron.right", "go-next-symbolic"),
    PLUS("plus", "list-add-symbolic"),
    MINUS("minus", "list-remove-symbolic"),
    TRASH("trash", "user-trash-symbolic"),
    SHARE("square.and.arrow.up", "emblem-shared-symbolic");

    private final String sfSymbolName;
    private final String gtkIconName;

    SystemIcon(String sfSymbolName, String gtkIconName) {
        this.sfSymbolName = sfSymbolName;
        this.gtkIconName = gtkIconName;
    }

    /** The macOS SF Symbol identifier (e.g. {@code "heart.fill"}). */
    public String sfSymbolName() { return sfSymbolName; }

    /** The freedesktop/GTK icon-theme name (e.g. {@code "emblem-favorite-symbolic"}). */
    public String gtkIconName() { return gtkIconName; }
}
