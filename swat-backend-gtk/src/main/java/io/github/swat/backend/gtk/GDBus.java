package io.github.swat.backend.gtk;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * GLib GDBus FFM bindings. GDBus ships in libgio-2.0 (the same library that
 * also exports g_app_info_*, GListStore, GMenu, etc.) and is co-loaded
 * alongside libgtk-4.so.1. We reuse {@link Gtk#GLOBAL} as the symbol
 * lookup arena.
 *
 * <p>Used by {@code StatusNotifierItem} to speak the
 * {@code org.kde.StatusNotifierItem} protocol over the session bus, and
 * to export a {@code GMenuModel} via the dbusmenu protocol — GLib does the
 * dbusmenu translation for us via {@code g_dbus_connection_export_menu_model}.
 */
final class GDBus {
    private GDBus() {}

    /** {@code GBusType}: {@code G_BUS_TYPE_SESSION = 2}. */
    static final int G_BUS_TYPE_SESSION = 2;
    /** {@code GDBusCallFlags}: {@code G_DBUS_CALL_FLAGS_NONE = 0}. */
    static final int G_DBUS_CALL_FLAGS_NONE = 0;
    /** {@code GBusNameOwnerFlags}: {@code G_BUS_NAME_OWNER_FLAGS_NONE = 0}. */
    static final int G_BUS_NAME_OWNER_FLAGS_NONE = 0;

    /**
     * {@code GDBusInterfaceVTable}: three function pointers (method_call,
     * get_property, set_property) followed by 8 pointer-sized padding slots.
     */
    static final MemoryLayout INTERFACE_VTABLE = MemoryLayout.structLayout(
        ValueLayout.ADDRESS.withName("method_call"),
        ValueLayout.ADDRESS.withName("get_property"),
        ValueLayout.ADDRESS.withName("set_property"),
        MemoryLayout.sequenceLayout(8, ValueLayout.ADDRESS).withName("padding"));

    private static final SymbolLookup LIB =
        SymbolLookup.libraryLookup("libgio-2.0.so.0", Gtk.GLOBAL);

    private static MethodHandle bind(String symbol, FunctionDescriptor fd) {
        return Linker.nativeLinker().downcallHandle(
            LIB.find(symbol).orElseThrow(() ->
                new RuntimeException("Missing GDBus symbol: " + symbol)),
            fd);
    }

    private static final MethodHandle G_BUS_GET_SYNC =
        bind("g_bus_get_sync", FunctionDescriptor.of(Gtk.PTR, Gtk.INT, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_BUS_OWN_NAME_ON_CONNECTION =
        bind("g_bus_own_name_on_connection",
            FunctionDescriptor.of(Gtk.INT, Gtk.PTR, Gtk.PTR, Gtk.INT, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_BUS_UNOWN_NAME =
        bind("g_bus_unown_name", FunctionDescriptor.ofVoid(Gtk.INT));

    private static final MethodHandle G_DBUS_CONNECTION_CALL_SYNC =
        bind("g_dbus_connection_call_sync",
            FunctionDescriptor.of(Gtk.PTR,
                Gtk.PTR,        // connection
                Gtk.PTR,        // bus_name
                Gtk.PTR,        // object_path
                Gtk.PTR,        // interface
                Gtk.PTR,        // method
                Gtk.PTR,        // parameters (GVariant*, may be NULL)
                Gtk.PTR,        // reply_type (GVariantType*, may be NULL)
                Gtk.INT,        // flags
                Gtk.INT,        // timeout_msec
                Gtk.PTR,        // cancellable
                Gtk.PTR));      // error
    private static final MethodHandle G_DBUS_CONNECTION_REGISTER_OBJECT =
        bind("g_dbus_connection_register_object",
            FunctionDescriptor.of(Gtk.INT, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_DBUS_CONNECTION_UNREGISTER_OBJECT =
        bind("g_dbus_connection_unregister_object", FunctionDescriptor.of(Gtk.INT, Gtk.PTR, Gtk.INT));
    private static final MethodHandle G_DBUS_CONNECTION_EXPORT_MENU_MODEL =
        bind("g_dbus_connection_export_menu_model",
            FunctionDescriptor.of(Gtk.INT, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_DBUS_CONNECTION_UNEXPORT_MENU_MODEL =
        bind("g_dbus_connection_unexport_menu_model", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.INT));
    private static final MethodHandle G_DBUS_CONNECTION_EXPORT_ACTION_GROUP =
        bind("g_dbus_connection_export_action_group",
            FunctionDescriptor.of(Gtk.INT, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_DBUS_CONNECTION_UNEXPORT_ACTION_GROUP =
        bind("g_dbus_connection_unexport_action_group", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.INT));
    private static final MethodHandle G_DBUS_CONNECTION_EMIT_SIGNAL =
        bind("g_dbus_connection_emit_signal",
            FunctionDescriptor.of(Gtk.INT, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR, Gtk.PTR));

    private static final MethodHandle G_DBUS_NODE_INFO_NEW_FOR_XML =
        bind("g_dbus_node_info_new_for_xml", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_DBUS_NODE_INFO_LOOKUP_INTERFACE =
        bind("g_dbus_node_info_lookup_interface", FunctionDescriptor.of(Gtk.PTR, Gtk.PTR, Gtk.PTR));
    private static final MethodHandle G_DBUS_NODE_INFO_UNREF =
        bind("g_dbus_node_info_unref", FunctionDescriptor.ofVoid(Gtk.PTR));

    private static final MethodHandle G_DBUS_METHOD_INVOCATION_RETURN_VALUE =
        bind("g_dbus_method_invocation_return_value", FunctionDescriptor.ofVoid(Gtk.PTR, Gtk.PTR));

    /* ---------- Wrappers ---------- */

    /** {@code g_bus_get_sync} — returns a session-bus {@code GDBusConnection*}
     *  or {@code NULL} on error. */
    static MemorySegment g_bus_get_sync(int busType) {
        try { return (MemorySegment) G_BUS_GET_SYNC.invoke(busType, MemorySegment.NULL, MemorySegment.NULL); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** {@code g_bus_own_name_on_connection} — request a name on an existing
     *  connection. Returns the name-watch ID; pass to
     *  {@link #g_bus_unown_name(int)} when releasing. */
    static int g_bus_own_name_on_connection(MemorySegment connection, String name, int flags) {
        try (var arena = Arena.ofConfined()) {
            return (int) G_BUS_OWN_NAME_ON_CONNECTION.invoke(
                connection, arena.allocateFrom(name), flags,
                MemorySegment.NULL, MemorySegment.NULL,
                MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_bus_unown_name(int id) {
        try { G_BUS_UNOWN_NAME.invoke(id); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** {@code g_dbus_connection_call_sync}. Returns the reply {@code GVariant*}
     *  or {@code NULL} on error; caller must {@link #g_variant_unref(MemorySegment)}. */
    static MemorySegment g_dbus_connection_call_sync(
            MemorySegment connection, String busName, String objectPath, String iface,
            String method, MemorySegment parameters, MemorySegment replyType,
            int flags, int timeoutMs) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) G_DBUS_CONNECTION_CALL_SYNC.invoke(
                connection,
                busName == null ? MemorySegment.NULL : arena.allocateFrom(busName),
                objectPath == null ? MemorySegment.NULL : arena.allocateFrom(objectPath),
                iface == null ? MemorySegment.NULL : arena.allocateFrom(iface),
                method == null ? MemorySegment.NULL : arena.allocateFrom(method),
                parameters == null ? MemorySegment.NULL : parameters,
                replyType == null ? MemorySegment.NULL : replyType,
                flags, timeoutMs,
                MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int g_dbus_connection_register_object(MemorySegment connection, String objectPath,
                                                 MemorySegment interfaceInfo, MemorySegment vtable,
                                                 MemorySegment userData) {
        try (var arena = Arena.ofConfined()) {
            return (int) G_DBUS_CONNECTION_REGISTER_OBJECT.invoke(
                connection, arena.allocateFrom(objectPath),
                interfaceInfo, vtable, userData,
                MemorySegment.NULL,    // user_data_free_func
                MemorySegment.NULL);   // error
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_dbus_connection_unregister_object(MemorySegment connection, int registrationId) {
        try { G_DBUS_CONNECTION_UNREGISTER_OBJECT.invoke(connection, registrationId); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int g_dbus_connection_export_menu_model(MemorySegment connection, String objectPath,
                                                   MemorySegment menuModel) {
        try (var arena = Arena.ofConfined()) {
            return (int) G_DBUS_CONNECTION_EXPORT_MENU_MODEL.invoke(
                connection, arena.allocateFrom(objectPath), menuModel, MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_dbus_connection_unexport_menu_model(MemorySegment connection, int exportId) {
        try { G_DBUS_CONNECTION_UNEXPORT_MENU_MODEL.invoke(connection, exportId); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static int g_dbus_connection_export_action_group(MemorySegment connection, String objectPath,
                                                     MemorySegment actionGroup) {
        try (var arena = Arena.ofConfined()) {
            return (int) G_DBUS_CONNECTION_EXPORT_ACTION_GROUP.invoke(
                connection, arena.allocateFrom(objectPath), actionGroup, MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_dbus_connection_unexport_action_group(MemorySegment connection, int exportId) {
        try { G_DBUS_CONNECTION_UNEXPORT_ACTION_GROUP.invoke(connection, exportId); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    /** Emit a signal from {@code path} on {@code iface}. {@code parameters}
     *  may be NULL for parameterless signals. */
    static void g_dbus_connection_emit_signal(MemorySegment connection, String path,
                                              String iface, String signal,
                                              MemorySegment parameters) {
        try (var arena = Arena.ofConfined()) {
            G_DBUS_CONNECTION_EMIT_SIGNAL.invoke(
                connection, MemorySegment.NULL,
                arena.allocateFrom(path), arena.allocateFrom(iface),
                arena.allocateFrom(signal),
                parameters == null ? MemorySegment.NULL : parameters,
                MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment g_dbus_node_info_new_for_xml(String xml) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) G_DBUS_NODE_INFO_NEW_FOR_XML.invoke(
                arena.allocateFrom(xml), MemorySegment.NULL);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static MemorySegment g_dbus_node_info_lookup_interface(MemorySegment node, String iface) {
        try (var arena = Arena.ofConfined()) {
            return (MemorySegment) G_DBUS_NODE_INFO_LOOKUP_INTERFACE.invoke(
                node, arena.allocateFrom(iface));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_dbus_node_info_unref(MemorySegment nodeInfo) {
        try { G_DBUS_NODE_INFO_UNREF.invoke(nodeInfo); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    static void g_dbus_method_invocation_return_value(MemorySegment invocation, MemorySegment reply) {
        try {
            G_DBUS_METHOD_INVOCATION_RETURN_VALUE.invoke(
                invocation, reply == null ? MemorySegment.NULL : reply);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }
}
