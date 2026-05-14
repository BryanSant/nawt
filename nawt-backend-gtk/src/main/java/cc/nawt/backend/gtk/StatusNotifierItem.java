package cc.nawt.backend.gtk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure-Java implementation of the {@code org.kde.StatusNotifierItem}
 * (KDE / freedesktop tray) protocol. Built on GLib's GDBus, so we lean on
 * {@code g_dbus_connection_export_menu_model} to translate a {@code GMenu}
 * into the {@code com.canonical.dbusmenu} protocol automatically (the
 * single largest piece of work we'd otherwise need to implement by hand).
 *
 * <p>Reference: see {@code ../libayatana-appindicator-glib/src/ayatana-appindicator.c}
 * for the canonical C version of this protocol setup.
 */
final class StatusNotifierItem {

    private static final String SNI_INTERFACE = "org.kde.StatusNotifierItem";
    private static final String SNI_OBJECT_PATH = "/StatusNotifierItem";
    private static final String MENU_OBJECT_PATH = "/MenuBar";
    private static final String WATCHER_BUS_NAME = "org.kde.StatusNotifierWatcher";
    private static final String WATCHER_OBJECT_PATH = "/StatusNotifierWatcher";

    /**
     * SNI interface XML — a deliberate subset. Full pixmap support and the
     * {@code ToolTip} struct are omitted for tier-2 simplicity; the host's
     * fall-back is to display {@code Title} on hover, which gives us
     * tooltip-equivalent UX for free.
     */
    private static final String SNI_XML = """
        <node>
            <interface name="org.kde.StatusNotifierItem">
                <property name="Id"            type="s" access="read" />
                <property name="Category"      type="s" access="read" />
                <property name="Title"         type="s" access="read" />
                <property name="Status"        type="s" access="read" />
                <property name="IconName"      type="s" access="read" />
                <property name="IconThemePath" type="s" access="read" />
                <property name="Menu"          type="o" access="read" />
                <property name="ItemIsMenu"    type="b" access="read" />
                <method name="Activate">
                    <arg type="i" name="x" direction="in" />
                    <arg type="i" name="y" direction="in" />
                </method>
                <method name="ContextMenu">
                    <arg type="i" name="x" direction="in" />
                    <arg type="i" name="y" direction="in" />
                </method>
                <signal name="NewIcon" />
                <signal name="NewTitle" />
                <signal name="NewStatus">
                    <arg type="s" name="status" direction="out" />
                </signal>
            </interface>
        </node>
        """;

    /* ---------- shared callback infrastructure ---------- */

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, StatusNotifierItem> INSTANCES = new ConcurrentHashMap<>();

    /** Cached parsed XML — single instance shared across all trays. */
    private static final MemorySegment NODE_INFO;
    private static final MemorySegment INTERFACE_INFO;
    private static final MemorySegment METHOD_CALL_STUB;
    private static final MemorySegment GET_PROPERTY_STUB;

    static {
        NODE_INFO = GDBus.g_dbus_node_info_new_for_xml(SNI_XML);
        if (NODE_INFO == null || NODE_INFO.address() == 0) {
            throw new RuntimeException("g_dbus_node_info_new_for_xml failed for SNI XML");
        }
        INTERFACE_INFO = GDBus.g_dbus_node_info_lookup_interface(NODE_INFO, SNI_INTERFACE);
        if (INTERFACE_INFO == null || INTERFACE_INFO.address() == 0) {
            throw new RuntimeException("Could not look up " + SNI_INTERFACE);
        }

        try {
            // void(GDBusConnection*, const gchar* sender, const gchar* path,
            //      const gchar* iface, const gchar* method, GVariant* params,
            //      GDBusMethodInvocation* invocation, gpointer user_data)
            MethodHandle mcMh = MethodHandles.lookup().findStatic(
                StatusNotifierItem.class, "methodCallCallback",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class));
            METHOD_CALL_STUB = Linker.nativeLinker().upcallStub(
                mcMh,
                FunctionDescriptor.ofVoid(
                    Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);

            // GVariant*(GDBusConnection*, const gchar* sender, const gchar* path,
            //           const gchar* iface, const gchar* property,
            //           GError** error, gpointer user_data)
            MethodHandle gpMh = MethodHandles.lookup().findStatic(
                StatusNotifierItem.class, "getPropertyCallback",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class));
            GET_PROPERTY_STUB = Linker.nativeLinker().upcallStub(
                gpMh,
                FunctionDescriptor.of(
                    Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /* ---------- per-instance state ---------- */

    private final long token;
    private final String busName;
    private final MemorySegment connection;
    private final int nameOwnerId;
    private final int objectRegistrationId;
    private int menuExportId;
    private int actionsExportId;

    private volatile String id;
    private volatile String title;          // shown as the hover tooltip on most hosts
    private volatile String iconName;       // theme icon name
    private volatile String iconThemePath;  // dir of a path-based icon
    private volatile String status;

    StatusNotifierItem(String id, String tooltip, String iconNameOrPath, MemorySegment menuModel,
                       MemorySegment actionGroup) {
        this.token = NEXT_TOKEN.getAndIncrement();
        this.id = (id == null || id.isEmpty()) ? "nawt-tray" : id;
        this.title = tooltip == null ? "" : tooltip;
        this.status = "Active";
        applyIcon(iconNameOrPath);
        INSTANCES.put(token, this);

        this.connection = GDBus.g_bus_get_sync(GDBus.G_BUS_TYPE_SESSION);
        if (connection == null || connection.address() == 0) {
            INSTANCES.remove(token);
            throw new RuntimeException("Could not connect to the session bus");
        }
        this.busName = "org.kde.StatusNotifierItem-" + ProcessHandle.current().pid() + "-" + token;
        this.nameOwnerId = GDBus.g_bus_own_name_on_connection(
            connection, busName, GDBus.G_BUS_NAME_OWNER_FLAGS_NONE);

        try (var arena = Arena.ofConfined()) {
            MemorySegment vtable = arena.allocate(GDBus.INTERFACE_VTABLE);
            vtable.fill((byte) 0);
            vtable.set(ValueLayout.ADDRESS, 0, METHOD_CALL_STUB);                  // method_call
            vtable.set(ValueLayout.ADDRESS, ValueLayout.ADDRESS.byteSize(),
                GET_PROPERTY_STUB);                                                  // get_property
            // set_property left NULL — all SNI props are read-only.
            this.objectRegistrationId = GDBus.g_dbus_connection_register_object(
                connection, SNI_OBJECT_PATH, INTERFACE_INFO, vtable,
                MemorySegment.ofAddress(token));
        }
        if (objectRegistrationId == 0) {
            cleanupOwning();
            throw new RuntimeException("g_dbus_connection_register_object failed");
        }

        if (menuModel != null && menuModel.address() != 0) {
            this.menuExportId = GDBus.g_dbus_connection_export_menu_model(
                connection, MENU_OBJECT_PATH, menuModel);
        }
        if (actionGroup != null && actionGroup.address() != 0) {
            this.actionsExportId = GDBus.g_dbus_connection_export_action_group(
                connection, MENU_OBJECT_PATH, actionGroup);
        }

        registerWithWatcher();
    }

    private void registerWithWatcher() {
        try (var arena = Arena.ofConfined()) {
            MemorySegment param = GVariant.refSink(GVariant.newTuple(
                arena, GVariant.newString(busName)));
            try {
                MemorySegment reply = GDBus.g_dbus_connection_call_sync(
                    connection,
                    WATCHER_BUS_NAME, WATCHER_OBJECT_PATH, WATCHER_BUS_NAME,
                    "RegisterStatusNotifierItem",
                    param,
                    MemorySegment.NULL,
                    GDBus.G_DBUS_CALL_FLAGS_NONE,
                    -1);
                if (reply != null && reply.address() != 0) GVariant.unref(reply);
            } finally {
                GVariant.unref(param);
            }
        }
    }

    /* ---------- public mutators ---------- */

    void setIconNameOrPath(String iconNameOrPath) {
        applyIcon(iconNameOrPath);
        emitSignal("NewIcon", null);
    }

    void setTooltip(String tooltip) {
        this.title = tooltip == null ? "" : tooltip;
        emitSignal("NewTitle", null);
    }

    void setStatus(String newStatus) {
        if (newStatus == null || newStatus.isBlank()) newStatus = "Active";
        this.status = newStatus;
        try (var arena = Arena.ofConfined()) {
            MemorySegment param = GVariant.refSink(GVariant.newTuple(
                arena, GVariant.newString(this.status)));
            emitSignal("NewStatus", param);
            GVariant.unref(param);
        }
    }

    private void emitSignal(String signal, MemorySegment params) {
        GDBus.g_dbus_connection_emit_signal(
            connection, SNI_OBJECT_PATH, SNI_INTERFACE, signal, params);
    }

    void close() {
        if (objectRegistrationId != 0) {
            GDBus.g_dbus_connection_unregister_object(connection, objectRegistrationId);
        }
        if (menuExportId != 0) {
            GDBus.g_dbus_connection_unexport_menu_model(connection, menuExportId);
            menuExportId = 0;
        }
        if (actionsExportId != 0) {
            GDBus.g_dbus_connection_unexport_action_group(connection, actionsExportId);
            actionsExportId = 0;
        }
        cleanupOwning();
        Gtk.g_object_unref(connection);
        INSTANCES.remove(token);
    }

    private void cleanupOwning() {
        if (nameOwnerId != 0) GDBus.g_bus_unown_name(nameOwnerId);
    }

    /* ---------- icon name / theme path resolution ---------- */

    private void applyIcon(String iconNameOrPath) {
        if (iconNameOrPath == null || iconNameOrPath.isEmpty()) {
            this.iconName = "";
            this.iconThemePath = "";
            return;
        }
        if (iconNameOrPath.startsWith("/")) {
            // Treat as a filesystem path: the host resolves an icon-named file
            // inside IconThemePath. Strip the extension off the basename.
            int slash = iconNameOrPath.lastIndexOf('/');
            String dir = slash > 0 ? iconNameOrPath.substring(0, slash) : "";
            String base = iconNameOrPath.substring(slash + 1);
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            this.iconName = base;
            this.iconThemePath = dir;
        } else {
            this.iconName = iconNameOrPath;
            this.iconThemePath = "";
        }
    }

    /* ---------- upcalled callbacks ---------- */

    @SuppressWarnings("unused")
    private static MemorySegment getPropertyCallback(
            MemorySegment connection, MemorySegment sender, MemorySegment objectPath,
            MemorySegment iface, MemorySegment property,
            MemorySegment errorOut, MemorySegment userData) {
        StatusNotifierItem self = INSTANCES.get(userData.address());
        if (self == null) return MemorySegment.NULL;
        String prop = property.reinterpret(Long.MAX_VALUE).getString(0);
        try {
            return switch (prop) {
                case "Id"            -> GVariant.newString(self.id);
                case "Category"      -> GVariant.newString("ApplicationStatus");
                case "Title"         -> GVariant.newString(self.title);
                case "Status"        -> GVariant.newString(self.status);
                case "IconName"      -> GVariant.newString(self.iconName);
                case "IconThemePath" -> GVariant.newString(self.iconThemePath);
                case "Menu"          -> GVariant.newObjectPath(MENU_OBJECT_PATH);
                case "ItemIsMenu"    -> GVariant.newBoolean(true);
                default              -> MemorySegment.NULL;
            };
        } catch (Throwable t) {
            t.printStackTrace();
            return MemorySegment.NULL;
        }
    }

    @SuppressWarnings("unused")
    private static void methodCallCallback(
            MemorySegment connection, MemorySegment sender, MemorySegment objectPath,
            MemorySegment iface, MemorySegment method, MemorySegment params,
            MemorySegment invocation, MemorySegment userData) {
        // Activate / ContextMenu / SecondaryActivate / Scroll all return void.
        // Tier-2 scope: respond with an empty reply so the host doesn't error.
        // Click handling is delivered through the menu's GAction callbacks,
        // not through these methods.
        try {
            GDBus.g_dbus_method_invocation_return_value(invocation, MemorySegment.NULL);
        } catch (Throwable t) { t.printStackTrace(); }
    }
}
