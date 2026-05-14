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
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * GTK4 drag-and-drop wiring. Tier-1 supports text payloads only.
 *
 * <p><b>Source side</b>: a {@link Gtk#gtk_drag_source_new() GtkDragSource}
 * controller is attached via {@link Gtk#gtk_widget_add_controller}. The
 * {@code prepare} signal fires when the user starts a drag; the upcall builds
 * a {@code GValue} of type {@code G_TYPE_STRING} from the supplier's text and
 * wraps it with {@code gdk_content_provider_new_for_value}.
 *
 * <p><b>Destination side</b>: a {@link Gtk#gtk_drop_target_new(long, int)
 * GtkDropTarget} controller is created for {@code G_TYPE_STRING} and attached
 * the same way. The {@code drop} signal upcall extracts the {@code GValue}
 * with {@code g_value_get_string} and forwards it to the consumer.
 */
final class GtkDnD {
    private GtkDnD() {}

    private static final AtomicLong NEXT_TOKEN = new AtomicLong(1L);
    private static final ConcurrentHashMap<Long, Supplier<String>> DRAG_PROVIDERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Consumer<String>> DROP_HANDLERS = new ConcurrentHashMap<>();

    /** widget-pointer → token used to dispatch into the maps above. */
    private static final ConcurrentHashMap<Long, Long> WIDGET_TO_DRAG_TOKEN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> WIDGET_TO_DROP_TOKEN = new ConcurrentHashMap<>();

    private static final MemorySegment PREPARE_STUB;
    private static final MemorySegment DROP_STUB;

    static {
        try {
            // GdkContentProvider* prepare(GtkDragSource*, gdouble x, gdouble y, gpointer user_data)
            MethodHandle prepareMh = MethodHandles.lookup().findStatic(
                GtkDnD.class, "prepareCallback",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, double.class, double.class, MemorySegment.class));
            PREPARE_STUB = Linker.nativeLinker().upcallStub(
                prepareMh,
                FunctionDescriptor.of(Gtk.PTR,
                    Gtk.PTR, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, Gtk.PTR),
                Gtk.GLOBAL);

            // gboolean drop(GtkDropTarget*, const GValue* value, gdouble x, gdouble y, gpointer user_data)
            MethodHandle dropMh = MethodHandles.lookup().findStatic(
                GtkDnD.class, "dropCallback",
                MethodType.methodType(int.class,
                    MemorySegment.class, MemorySegment.class,
                    double.class, double.class, MemorySegment.class));
            DROP_STUB = Linker.nativeLinker().upcallStub(
                dropMh,
                FunctionDescriptor.of(Gtk.BOOL,
                    Gtk.PTR, Gtk.PTR,
                    ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, Gtk.PTR),
                Gtk.GLOBAL);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    static void setDragSource(MemorySegment widget, Supplier<String> textProvider) {
        if (textProvider == null) {
            Long t = WIDGET_TO_DRAG_TOKEN.get(widget.address());
            if (t != null) DRAG_PROVIDERS.remove(t);
            return;
        }
        Long existing = WIDGET_TO_DRAG_TOKEN.get(widget.address());
        if (existing != null) {
            DRAG_PROVIDERS.put(existing, textProvider);
            return;
        }
        long token = NEXT_TOKEN.getAndIncrement();
        DRAG_PROVIDERS.put(token, textProvider);
        WIDGET_TO_DRAG_TOKEN.put(widget.address(), token);

        MemorySegment source = Gtk.gtk_drag_source_new();
        Gtk.gtk_drag_source_set_actions(source, Gtk.GDK_ACTION_COPY);
        MemorySegment data = MemorySegment.ofAddress(token);
        Gtk.g_signal_connect_data(source, "prepare", PREPARE_STUB, data,
            MemorySegment.NULL, 0);
        // gtk_widget_add_controller takes ownership of the controller.
        Gtk.gtk_widget_add_controller(widget, source);
    }

    static void setDropTarget(MemorySegment widget, Consumer<String> textHandler) {
        if (textHandler == null) {
            Long t = WIDGET_TO_DROP_TOKEN.get(widget.address());
            if (t != null) DROP_HANDLERS.remove(t);
            return;
        }
        Long existing = WIDGET_TO_DROP_TOKEN.get(widget.address());
        if (existing != null) {
            DROP_HANDLERS.put(existing, textHandler);
            return;
        }
        long token = NEXT_TOKEN.getAndIncrement();
        DROP_HANDLERS.put(token, textHandler);
        WIDGET_TO_DROP_TOKEN.put(widget.address(), token);

        MemorySegment target = Gtk.gtk_drop_target_new(Gtk.G_TYPE_STRING, Gtk.GDK_ACTION_COPY);
        MemorySegment data = MemorySegment.ofAddress(token);
        Gtk.g_signal_connect_data(target, "drop", DROP_STUB, data,
            MemorySegment.NULL, 0);
        Gtk.gtk_widget_add_controller(widget, target);
    }

    @SuppressWarnings("unused") // upcalled from GTK
    private static MemorySegment prepareCallback(MemorySegment source,
                                                 double x, double y,
                                                 MemorySegment userData) {
        Supplier<String> provider = DRAG_PROVIDERS.get(userData.address());
        if (provider == null) return MemorySegment.NULL;
        String text;
        try { text = provider.get(); }
        catch (Throwable t) { t.printStackTrace(); return MemorySegment.NULL; }
        if (text == null || text.isEmpty()) return MemorySegment.NULL;
        try (var arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(Gtk.G_VALUE);
            // GValue must be zero-initialised before g_value_init.
            value.fill((byte) 0);
            Gtk.g_value_init(value, Gtk.G_TYPE_STRING);
            MemorySegment cstr = arena.allocateFrom(text);
            Gtk.g_value_set_string(value, cstr);
            MemorySegment provider2 = Gtk.gdk_content_provider_new_for_value(value);
            Gtk.g_value_unset(value);
            return provider2;
        }
    }

    @SuppressWarnings("unused") // upcalled from GTK
    private static int dropCallback(MemorySegment target, MemorySegment value,
                                    double x, double y, MemorySegment userData) {
        Consumer<String> handler = DROP_HANDLERS.get(userData.address());
        if (handler == null) return 0;
        try {
            MemorySegment cstr = Gtk.g_value_get_string(value);
            String text = (cstr == null || cstr.address() == 0)
                ? "" : cstr.reinterpret(Long.MAX_VALUE).getString(0);
            handler.accept(text);
            return 1;
        } catch (Throwable t) {
            t.printStackTrace();
            return 0;
        }
    }
}
