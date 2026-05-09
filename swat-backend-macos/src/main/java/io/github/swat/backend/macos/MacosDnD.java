package io.github.swat.backend.macos;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AppKit drag-and-drop wiring. Tier-1 supports text payloads only.
 *
 * <p><b>Source side</b>: an {@code NSPanGestureRecognizer} is attached to the
 * peer view; on {@code .began} we synthesise an {@code NSDraggingItem} from the
 * supplier's text and call {@code beginDraggingSessionWithItems:event:source:}.
 * A shared {@code SwatDragSource} class conforms to {@code NSDraggingSource}
 * and answers {@code NSDragOperationCopy} from
 * {@code draggingSession:sourceOperationMaskForDraggingContext:}.
 *
 * <p><b>Destination side</b>: the view's class is replaced (KVO-style isa
 * swizzling) with a runtime-built subclass that overrides
 * {@code draggingEntered:} / {@code draggingUpdated:} /
 * {@code performDragOperation:}. Subclasses are cached per source class so
 * each AppKit class is swizzled at most once.
 */
final class MacosDnD {
    private MacosDnD() {}

    /* ---------- public entry points ---------- */

    static void setDragSource(MemorySegment view, Supplier<String> textProvider) {
        if (textProvider == null) {
            DRAG_PROVIDERS.remove(view.address());
            return;
        }
        DRAG_PROVIDERS.put(view.address(), textProvider);
        if (DRAG_GESTURES_INSTALLED.putIfAbsent(view.address(), Boolean.TRUE) == null) {
            installPanGestureRecognizer(view);
        }
    }

    static void setDropTarget(MemorySegment view, Consumer<String> textHandler) {
        if (textHandler == null) {
            DROP_HANDLERS.remove(view.address());
            return;
        }
        DROP_HANDLERS.put(view.address(), textHandler);
        if (DROP_INSTALLED.putIfAbsent(view.address(), Boolean.TRUE) == null) {
            installDropOnView(view);
        }
    }

    /* ---------- shared drag source object (NSDraggingSource) ---------- */

    private static final MemorySegment DRAG_SOURCE_CLASS;
    private static final MemorySegment DRAG_SOURCE_INSTANCE;
    private static final MemorySegment PAN_ACTION_SEL = Objc.sel("swatDnDPan:");

    /** view-pointer → text supplier. */
    private static final ConcurrentHashMap<Long, Supplier<String>> DRAG_PROVIDERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DRAG_GESTURES_INSTALLED = new ConcurrentHashMap<>();

    /** view-pointer → text consumer. */
    private static final ConcurrentHashMap<Long, Consumer<String>> DROP_HANDLERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Boolean> DROP_INSTALLED = new ConcurrentHashMap<>();

    /** original-class-name → swizzled subclass. */
    private static final ConcurrentHashMap<String, MemorySegment> DROP_SUBCLASSES = new ConcurrentHashMap<>();

    private static final long NS_DRAG_OP_COPY = 1L;

    /** NSPanGestureRecognizer state values. */
    private static final long GESTURE_STATE_BEGAN = 1L;

    static {
        DRAG_SOURCE_CLASS = registerDragSourceClass();
        DRAG_SOURCE_INSTANCE = Objc.sendPtr(Objc.send_alloc(DRAG_SOURCE_CLASS), Objc.sel("init"));
    }

    /* ---------- gesture / dragging session ---------- */

    private static void installPanGestureRecognizer(MemorySegment view) {
        // [[NSPanGestureRecognizer alloc] initWithTarget:DRAG_SOURCE_INSTANCE action:@selector(swatDnDPan:)]
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSPanGestureRecognizer"));
        MemorySegment recog;
        try {
            recog = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(alloc, Objc.sel("initWithTarget:action:"),
                    DRAG_SOURCE_INSTANCE, PAN_ACTION_SEL);
        } catch (Throwable t) { throw new RuntimeException(t); }
        // Don't swallow primary mouse events — let normal click/select work.
        Objc.sendVoidBool(recog, Objc.sel("setDelaysPrimaryMouseButtonEvents:"), false);
        Objc.sendVoid(view, Objc.sel("addGestureRecognizer:"), recog);
        // recognizer is retained by the view; we don't keep our own reference.
        Objc.sendVoid(recog, Objc.sel("release"));
    }

    @SuppressWarnings("unused") // upcalled from native via swatDnDPan:
    private static void panAction(MemorySegment self, MemorySegment cmd, MemorySegment recognizer) {
        try {
            long state = Objc.sendLong(recognizer, Objc.sel("state"));
            if (state != GESTURE_STATE_BEGAN) return;
            MemorySegment view = Objc.sendPtr(recognizer, Objc.sel("view"));
            if (view == null || view.address() == 0) return;
            Supplier<String> provider = DRAG_PROVIDERS.get(view.address());
            if (provider == null) return;
            String text;
            try { text = provider.get(); }
            catch (Throwable t) { t.printStackTrace(); return; }
            if (text == null || text.isEmpty()) return;
            beginDraggingSession(view, text);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private static void beginDraggingSession(MemorySegment view, String text) {
        // NSString is the pasteboard writer.
        MemorySegment ns = NSString.from(text);
        // [[NSDraggingItem alloc] initWithPasteboardWriter:ns]
        MemorySegment item = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSDraggingItem")),
            Objc.sel("initWithPasteboardWriter:"), ns);
        // [item setDraggingFrame:[view bounds] contents:nil] — the system supplies a
        // default text drag image when contents is nil and a frame is provided.
        try (var arena = java.lang.foreign.Arena.ofConfined()) {
            MemorySegment bounds = arena.allocate(NS_RECT);
            // Read view.bounds via objc_msgSend_stret-equivalent: arm64 returns NSRect in registers.
            try {
                // -[NSView bounds] returns NSRect by value. On arm64/x86_64 this is 4 doubles.
                MemorySegment b = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        NS_RECT, Objc.PTR, Objc.PTR))
                    .invoke(arena, view, Objc.sel("bounds"));
                MemorySegment.copy(b, 0, bounds, 0, NS_RECT.byteSize());
            } catch (Throwable t) { /* fallback: leave bounds zeroed */ }
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(
                        Objc.PTR, Objc.PTR, NS_RECT, Objc.PTR))
                    .invoke(item, Objc.sel("setDraggingFrame:contents:"),
                        bounds, Objc.NIL);
            } catch (Throwable t) { /* drag will still work without a frame */ }

            // items array: [NSArray arrayWithObject:item]
            MemorySegment items = Objc.sendPtr(
                Objc.cls("NSArray"), Objc.sel("arrayWithObject:"), item);
            MemorySegment event = Objc.sendPtr(Objc.cls("NSApplication"), Objc.sel("sharedApplication"));
            event = Objc.sendPtr(event, Objc.sel("currentEvent"));
            if (event == null || event.address() == 0) {
                Objc.sendVoid(item, Objc.sel("release"));
                return;
            }
            try {
                Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                    .invoke(view,
                        Objc.sel("beginDraggingSessionWithItems:event:source:"),
                        items, event, DRAG_SOURCE_INSTANCE);
            } catch (Throwable t) { throw new RuntimeException(t); }
            Objc.sendVoid(item, Objc.sel("release"));
        }
    }

    /* ---------- destination side ---------- */

    /** Retained to outlive any autorelease pool, since we read it on every drop. */
    private static final MemorySegment NS_PASTEBOARD_TYPE_STRING =
        Objc.sendPtr(NSString.from("public.utf8-plain-text"), Objc.sel("retain"));

    private static void installDropOnView(MemorySegment view) {
        // [view registerForDraggedTypes:@[NSPasteboardTypeString]]
        MemorySegment types = Objc.sendPtr(
            Objc.cls("NSArray"), Objc.sel("arrayWithObject:"), NS_PASTEBOARD_TYPE_STRING);
        Objc.sendVoid(view, Objc.sel("registerForDraggedTypes:"), types);

        // Swap class to a subclass with our drop methods.
        MemorySegment origCls = Objc.getClass(view);
        String origName = Objc.getClassName(origCls);
        MemorySegment subCls = DROP_SUBCLASSES.computeIfAbsent(origName, n -> buildDropSubclass(origCls, n));
        Objc.setClass(view, subCls);
    }

    private static MemorySegment buildDropSubclass(MemorySegment origCls, String origName) {
        String subName = "SwatDrop_" + origName;
        MemorySegment cls = Objc.allocateClassPair(origCls, subName);
        try {
            // -(NSDragOperation)draggingEntered:(id<NSDraggingInfo>)sender → "Q@:@"
            MethodHandle enteredMh = MethodHandles.lookup().findStatic(
                MacosDnD.class, "draggingEntered",
                MethodType.methodType(long.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment enteredImp = Linker.nativeLinker().upcallStub(
                enteredMh,
                FunctionDescriptor.of(Objc.NSUINT, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("draggingEntered:"), enteredImp, "Q@:@");

            // -(NSDragOperation)draggingUpdated:(id<NSDraggingInfo>)sender → reuse same imp
            Objc.addMethod(cls, Objc.sel("draggingUpdated:"), enteredImp, "Q@:@");

            // -(BOOL)performDragOperation:(id<NSDraggingInfo>)sender → "B@:@"
            MethodHandle performMh = MethodHandles.lookup().findStatic(
                MacosDnD.class, "performDragOperation",
                MethodType.methodType(boolean.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment performImp = Linker.nativeLinker().upcallStub(
                performMh,
                FunctionDescriptor.of(Objc.BOOL, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("performDragOperation:"), performImp, "B@:@");

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static long draggingEntered(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        // Always advertise copy if we have a registered handler.
        return DROP_HANDLERS.containsKey(self.address()) ? NS_DRAG_OP_COPY : 0L;
    }

    @SuppressWarnings("unused")
    private static boolean performDragOperation(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        Consumer<String> handler = DROP_HANDLERS.get(self.address());
        if (handler == null) return false;
        try {
            MemorySegment pb = Objc.sendPtr(sender, Objc.sel("draggingPasteboard"));
            if (pb == null || pb.address() == 0) return false;
            MemorySegment ns = Objc.sendPtr(pb, Objc.sel("stringForType:"), NS_PASTEBOARD_TYPE_STRING);
            if (ns == null || ns.address() == 0) return false;
            String text = NSString.toJava(ns);
            handler.accept(text == null ? "" : text);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /* ---------- NSDraggingSource registration ---------- */

    private static MemorySegment registerDragSourceClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "SwatDragSource");
        try {
            // -(NSDragOperation)draggingSession:(NSDraggingSession*)session
            //     sourceOperationMaskForDraggingContext:(NSDraggingContext)ctx → "Q@:@q"
            MethodHandle maskMh = MethodHandles.lookup().findStatic(
                MacosDnD.class, "sourceOperationMask",
                MethodType.methodType(long.class,
                    MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, long.class));
            MemorySegment maskImp = Linker.nativeLinker().upcallStub(
                maskMh,
                FunctionDescriptor.of(Objc.NSUINT, Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT),
                Objc.GLOBAL);
            Objc.addMethod(cls,
                Objc.sel("draggingSession:sourceOperationMaskForDraggingContext:"),
                maskImp, "Q@:@q");

            // -(void)swatDnDPan:(NSPanGestureRecognizer*)recognizer → "v@:@"
            MethodHandle panMh = MethodHandles.lookup().findStatic(
                MacosDnD.class, "panAction",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment panImp = Linker.nativeLinker().upcallStub(
                panMh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, PAN_ACTION_SEL, panImp, "v@:@");

            // Declare conformance to NSDraggingSource so AppKit accepts us as the
            // session source. The protocol may or may not be queryable via
            // objc_getProtocol depending on whether the framework symbol got
            // pulled in; addProtocol no-ops on null.
            Objc.addProtocol(cls, Objc.getProtocol("NSDraggingSource"));

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static long sourceOperationMask(MemorySegment self, MemorySegment cmd,
                                            MemorySegment session, long ctx) {
        return NS_DRAG_OP_COPY;
    }

    /* ---------- NSRect layout (4 CGFloats: x, y, w, h) ---------- */

    private static final MemoryLayout NS_RECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("x"),
        ValueLayout.JAVA_DOUBLE.withName("y"),
        ValueLayout.JAVA_DOUBLE.withName("w"),
        ValueLayout.JAVA_DOUBLE.withName("h"));
}
