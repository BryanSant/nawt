package io.github.swat.backend.macos;

import io.github.swat.spi.UiLoop;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * macOS event loop. Drives a manual {@code [NSApp nextEventMatchingMask:...]}
 * pump (rather than {@code [NSApp run]}) so we can exit cleanly without posting
 * fake termination events.
 *
 * <p>A pending-work queue lets virtual threads schedule Runnables onto the UI
 * thread; a sentinel {@code NSApplicationDefined} event wakes the pump so the
 * queue is drained promptly.
 */
final class MacosUiLoop implements UiLoop {

    // NSEventType / NSEventMask constants
    private static final long NS_EVENT_TYPE_APP_DEFINED = 15L;
    private static final long NS_EVENT_MASK_ANY = -1L; // NSUIntegerMax

    private final Queue<Runnable> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean shouldExit = new AtomicBoolean(false);

    private volatile Thread uiThread;
    private MemorySegment nsApp;
    private MemorySegment distantFuture;
    private MemorySegment defaultRunLoopMode;
    private MemorySegment wakeupEvent;

    @Override
    public void bootstrap() {
        ensureMainThread();
        uiThread = Thread.currentThread();

        try (var pool = AutoreleasePool.push()) {
            // [NSApplication sharedApplication]
            nsApp = Objc.sendPtr(Objc.cls("NSApplication"), Objc.sel("sharedApplication"));

            // [NSApp setActivationPolicy: NSApplicationActivationPolicyRegular(0)]
            Objc.sendVoidLong(nsApp, Objc.sel("setActivationPolicy:"), 0L);

            // Install a default main menu before -finishLaunching so the app
            // menu (About <name> · separator · Quit <name>) is in place when
            // macOS first lays out the menu bar. A later
            // Window.builder().menuBar(...) replaces this with the user's menu.
            MacosMenuBarPeer defaultBar = new MacosMenuBarPeer();
            Objc.sendVoid(nsApp, Objc.sel("setMainMenu:"), defaultBar.nsMenu());

            // [NSApp finishLaunching]
            Objc.sendVoid(nsApp, Objc.sel("finishLaunching"));

            // [NSApp activateIgnoringOtherApps:YES]
            Objc.sendVoidBool(nsApp, Objc.sel("activateIgnoringOtherApps:"), true);

            // [NSDate distantFuture] — retain it so it survives across pools
            MemorySegment df = Objc.sendPtr(Objc.cls("NSDate"), Objc.sel("distantFuture"));
            distantFuture = Objc.sendPtr(df, Objc.sel("retain"));

            // NSDefaultRunLoopMode is an exported NSString* in Foundation
            MemorySegment modePtr = SymbolLookup.libraryLookup(
                "/System/Library/Frameworks/Foundation.framework/Foundation", Objc.GLOBAL)
                .find("NSDefaultRunLoopMode").orElseThrow();
            defaultRunLoopMode = modePtr.reinterpret(ValueLayout.ADDRESS.byteSize())
                .get(ValueLayout.ADDRESS, 0);

            // Pre-construct a wakeup NSEvent (NSApplicationDefined)
            wakeupEvent = buildWakeupEvent();
            // retain it so it survives autorelease pool drains
            wakeupEvent = Objc.sendPtr(wakeupEvent, Objc.sel("retain"));
        }
    }

    @Override
    public void run() {
        while (!shouldExit.get()) {
            try (var pool = AutoreleasePool.push()) {
                drainPending();
                if (shouldExit.get()) break;

                // [NSApp nextEventMatchingMask:NSAnyEventMask untilDate:distantFuture inMode:NSDefaultRunLoopMode dequeue:YES]
                MemorySegment event;
                try {
                    event = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR,
                        Objc.NSUINT, Objc.PTR, Objc.PTR, Objc.BOOL))
                        .invoke(nsApp, Objc.sel("nextEventMatchingMask:untilDate:inMode:dequeue:"),
                            NS_EVENT_MASK_ANY, distantFuture, defaultRunLoopMode, true);
                } catch (Throwable t) { throw new RuntimeException(t); }

                if (event != null && event.address() != 0) {
                    Objc.sendVoid(nsApp, Objc.sel("sendEvent:"), event);
                    Objc.sendVoid(nsApp, Objc.sel("updateWindows"));
                }
            }
        }
    }

    @Override
    public void quit() {
        if (shouldExit.compareAndSet(false, true)) {
            wakeMainThread();
        }
    }

    @Override
    public void invokeLater(Runnable work) {
        pending.offer(work);
        if (!isUiThread()) wakeMainThread();
    }

    @Override
    public <T> T invokeAndWait(Callable<T> work) {
        if (isUiThread()) {
            throw new IllegalStateException(
                "Ui.invokeAndWait must not be called from the UI thread; use invokeLater or call directly.");
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        invokeLater(() -> {
            try { future.complete(work.call()); }
            catch (Throwable t) { future.completeExceptionally(t); }
        });
        try {
            return future.get();
        } catch (Throwable t) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error er) throw er;
            throw new RuntimeException(cause);
        }
    }

    @Override
    public boolean isUiThread() {
        return uiThread != null && Thread.currentThread() == uiThread;
    }

    private void drainPending() {
        Runnable r;
        while ((r = pending.poll()) != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    private void wakeMainThread() {
        // [NSApp postEvent:wakeupEvent atStart:YES]
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR, Objc.BOOL))
                .invoke(nsApp, Objc.sel("postEvent:atStart:"), wakeupEvent, true);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    private MemorySegment buildWakeupEvent() {
        // NSPoint = struct { CGFloat x; CGFloat y; }  (2 * 8 bytes on arm64/x86_64)
        MemoryLayout nsPoint = MemoryLayout.structLayout(
            ValueLayout.JAVA_DOUBLE.withName("x"),
            ValueLayout.JAVA_DOUBLE.withName("y"));

        try (var arena = Arena.ofConfined()) {
            MemorySegment zeroPoint = arena.allocate(nsPoint);

            // +[NSEvent otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:]
            FunctionDescriptor fd = FunctionDescriptor.of(
                Objc.PTR,            // NSEvent*
                Objc.PTR,            // self (NSEvent class)
                Objc.PTR,            // _cmd (selector)
                Objc.NSUINT,         // type (NSEventType)
                nsPoint,             // location (NSPoint, by value)
                Objc.NSUINT,         // modifierFlags (NSEventModifierFlags)
                Objc.CGFLOAT,        // timestamp (NSTimeInterval = double)
                Objc.NSINT,          // windowNumber
                Objc.PTR,            // context (NSGraphicsContext*, nil)
                ValueLayout.JAVA_SHORT, // subtype
                Objc.NSINT,          // data1
                Objc.NSINT           // data2
            );

            MemorySegment event;
            try {
                event = (MemorySegment) Objc.msgSend(fd).invoke(
                    Objc.cls("NSEvent"),
                    Objc.sel("otherEventWithType:location:modifierFlags:timestamp:windowNumber:context:subtype:data1:data2:"),
                    NS_EVENT_TYPE_APP_DEFINED,
                    zeroPoint,
                    0L,
                    0.0,
                    0L,
                    Objc.NIL,
                    (short) 0,
                    0L,
                    0L);
            } catch (Throwable t) { throw new RuntimeException(t); }
            return event;
        }
    }

    private static void ensureMainThread() {
        // pthread_main_np() returns 1 if calling thread is the process's main thread.
        try {
            SymbolLookup libsystem = Linker.nativeLinker().defaultLookup();
            var sym = libsystem.find("pthread_main_np");
            if (sym.isEmpty()) return; // fall back: trust the caller
            int isMain = (int) Linker.nativeLinker().downcallHandle(
                sym.get(),
                FunctionDescriptor.of(ValueLayout.JAVA_INT))
                .invoke();
            if (isMain == 0) {
                throw new IllegalStateException(
                    "Toolkit.launch must be called from the JVM main thread on macOS. "
                    + "If invoking via a non-standard launcher, ensure the main() method runs on thread 0.");
            }
        } catch (RuntimeException re) { throw re; }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
