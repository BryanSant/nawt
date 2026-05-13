package io.github.swat.backend.macos;

import io.github.swat.spi.MenuBarPeer;
import io.github.swat.spi.Peer;
import io.github.swat.spi.WindowConfig;
import io.github.swat.spi.WindowPeer;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.BooleanSupplier;

final class MacosWindowPeer implements WindowPeer {

    // NSWindowStyleMask:
    //   NSWindowStyleMaskTitled         = 1 << 0
    //   NSWindowStyleMaskClosable       = 1 << 1
    //   NSWindowStyleMaskMiniaturizable = 1 << 2
    //   NSWindowStyleMaskResizable      = 1 << 3
    private static final long STYLE_MASK_BASE = (1L) | (1L << 1) | (1L << 2);
    private static final long STYLE_MASK_RESIZABLE = (1L << 3);

    // NSBackingStoreBuffered = 2
    private static final long NS_BACKING_BUFFERED = 2L;

    private static final MemoryLayout NSRECT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("x"),
        ValueLayout.JAVA_DOUBLE.withName("y"),
        ValueLayout.JAVA_DOUBLE.withName("w"),
        ValueLayout.JAVA_DOUBLE.withName("h"));

    private static final MemoryLayout NSSIZE = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("w"),
        ValueLayout.JAVA_DOUBLE.withName("h"));

    private final MemorySegment window;   // NSWindow, retained
    private final MemorySegment delegate; // SwatWindowDelegate, retained
    private int width;
    private int height;
    private volatile BooleanSupplier permitClose;

    MacosWindowPeer(WindowConfig cfg) {
        this.width = cfg.width();
        this.height = cfg.height();

        // [[NSWindow alloc] initWithContentRect:rect styleMask:mask backing:type defer:NO]
        MemorySegment alloc = Objc.send_alloc(Objc.cls("NSWindow"));

        try (var arena = Arena.ofConfined()) {
            MemorySegment rect = arena.allocate(NSRECT);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 100.0);  // x
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 100.0);  // y
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, (double) width);
            rect.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, (double) height);

            FunctionDescriptor initFd = FunctionDescriptor.of(
                Objc.PTR, Objc.PTR, Objc.PTR,
                NSRECT, Objc.NSUINT, Objc.NSUINT, Objc.BOOL);
            long styleMask = STYLE_MASK_BASE | (cfg.resizable() ? STYLE_MASK_RESIZABLE : 0L);
            MemorySegment w;
            try {
                w = (MemorySegment) Objc.msgSend(initFd).invoke(
                    alloc,
                    Objc.sel("initWithContentRect:styleMask:backing:defer:"),
                    rect, styleMask, NS_BACKING_BUFFERED, false);
            } catch (Throwable t) { throw new RuntimeException(t); }
            this.window = w; // alloc+init returns a +1 retain — already owned
        }

        // setReleasedWhenClosed:NO so we control lifetime
        Objc.sendVoidBool(window, Objc.sel("setReleasedWhenClosed:"), false);

        // Set the title
        Objc.sendVoid(window, Objc.sel("setTitle:"), NSString.from(cfg.title()));

        // Center the window
        Objc.sendVoid(window, Objc.sel("center"));

        // Install delegate
        this.delegate = Objc.sendPtr(Delegates.newWindowDelegate(), Objc.sel("retain"));
        Objc.sendVoid(window, Objc.sel("setDelegate:"), delegate);

        Delegates.WINDOW_SHOULD_CLOSE.put(delegate.address(), this::shouldClose);
    }

    private boolean shouldClose() {
        BooleanSupplier h = permitClose;
        if (h == null) return true;
        try { return h.getAsBoolean(); }
        catch (Throwable t) { t.printStackTrace(); return true; }
    }

    @Override
    public void setTitle(String title) {
        Objc.sendVoid(window, Objc.sel("setTitle:"), NSString.from(title));
    }

    @Override
    public void setSize(int w, int h) {
        this.width = w;
        this.height = h;
        try (var arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(NSSIZE);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, (double) w);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, (double) h);
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, NSSIZE))
                    .invoke(window, Objc.sel("setContentSize:"), size);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
    }

    @Override
    public void setContent(Peer content) {
        MemorySegment view = MacosContainerPeer.peerView(content);
        // Top-level content needs to fill the window. Switch this view to
        // autoresize-mask layout (NSStackView etc. still drive their own
        // children via Auto Layout — only the outer frame is window-driven)
        // and set width+height masks so the frame tracks the content rect.
        Objc.sendVoidBool(view, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), true);
        // NSViewWidthSizable = 2, NSViewHeightSizable = 16
        Objc.sendVoidLong(view, Objc.sel("setAutoresizingMask:"), 2L | 16L);
        Objc.sendVoid(window, Objc.sel("setContentView:"), view);
        // Re-apply the configured content size. setContentView: can trigger
        // an Auto Layout pass that resizes the window to the content's
        // fittingSize (often smaller than the configured size, especially
        // when content has finite intrinsic widths). Force the size back to
        // what the caller requested.
        try (var arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(NSSIZE);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, (double) width);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, (double) height);
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, NSSIZE))
                    .invoke(window, Objc.sel("setContentSize:"), size);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
    }

    @Override
    public void setMenuBar(MenuBarPeer bar) {
        // macOS has a single application-wide menu bar. The window-attached
        // menu bar becomes the active main menu when the window is shown.
        if (bar == null) {
            // Fall back to a minimal default if available; otherwise leave existing.
            return;
        }
        if (!(bar instanceof MacosMenuBarPeer mbp)) {
            throw new IllegalArgumentException("Foreign MenuBarPeer: " + bar.getClass());
        }
        MemorySegment nsApp = Objc.sendPtr(Objc.cls("NSApplication"), Objc.sel("sharedApplication"));
        Objc.sendVoid(nsApp, Objc.sel("setMainMenu:"), mbp.nsMenu());
    }

    @Override
    public void setHeaderBar(io.github.swat.spi.HeaderBarPeer bar) {
        if (bar == null) {
            Objc.sendVoid(window, Objc.sel("setToolbar:"), Objc.NIL);
            return;
        }
        if (!(bar instanceof MacosHeaderBarPeer hbp)) {
            throw new IllegalArgumentException("Foreign HeaderBarPeer: " + bar.getClass());
        }
        Objc.sendVoid(window, Objc.sel("setToolbar:"), hbp.toolbar());
        // NSWindowToolbarStyleUnified = 2 — the modern compact look that
        // merges the window title and toolbar into one row.
        Objc.sendVoidLong(window, Objc.sel("setToolbarStyle:"), 2L);
    }

    @Override
    public void show() {
        Objc.sendVoid(window, Objc.sel("makeKeyAndOrderFront:"), Objc.NIL);
    }

    @Override
    public void toast(String message, int timeoutMs) {
        // macOS has no native in-app toast widget. Build a minimal one: a label
        // inside a tinted NSBox positioned at bottom-center of the content view.
        // A Java-side ScheduledExecutor removes it after the timeout.
        MemorySegment contentView = Objc.sendPtr(window, Objc.sel("contentView"));
        if (contentView == null || contentView.address() == 0) return;

        // [NSBox alloc] init] — a styled container giving the toast a backdrop.
        MemorySegment box = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSBox")), Objc.sel("init"));
        // NSBoxCustom = 4 — lets us pick our own background.
        Objc.sendVoidLong(box, Objc.sel("setBoxType:"), 4L);
        // NSTitlePositionNoTitle = 0
        Objc.sendVoidLong(box, Objc.sel("setTitlePosition:"), 0L);
        // Content margins
        try (var arena = Arena.ofConfined()) {
            MemorySegment size = arena.allocate(NSSIZE);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, 12.0);
            size.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, 6.0);
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, NSSIZE))
                    .invoke(box, Objc.sel("setContentViewMargins:"), size);
            } catch (Throwable t) { /* margins are cosmetic */ }
        }

        // [NSTextField labelWithString:message]
        MemorySegment label = Objc.sendPtr(
            Objc.cls("NSTextField"), Objc.sel("labelWithString:"), NSString.from(message));
        Objc.sendVoid(box, Objc.sel("setContentView:"), label);

        // Don't translate autoresizing into constraints — we set the frame
        // explicitly below.
        Objc.sendVoidBool(box, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), true);

        // Compute a frame anchored at the bottom-center of the content view.
        try (var arena = Arena.ofConfined()) {
            MemorySegment cvBounds = arena.allocate(NSRECT);
            try {
                MemorySegment b = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        NSRECT, Objc.PTR, Objc.PTR))
                    .invoke(arena, contentView, Objc.sel("bounds"));
                MemorySegment.copy(b, 0, cvBounds, 0, NSRECT.byteSize());
            } catch (Throwable t) { /* fallback: zeros */ }
            double cvW = cvBounds.getAtIndex(ValueLayout.JAVA_DOUBLE, 2);
            double toastW = Math.min(420.0, Math.max(180.0, cvW * 0.6));
            double toastH = 36.0;
            double x = (cvW - toastW) / 2.0;
            double y = 24.0; // 24 px above the bottom edge

            MemorySegment frame = arena.allocate(NSRECT);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, x);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, y);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, toastW);
            frame.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, toastH);
            try {
                Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, NSRECT))
                    .invoke(box, Objc.sel("setFrame:"), frame);
            } catch (Throwable t) { throw new RuntimeException(t); }
        }
        Objc.sendVoid(contentView, Objc.sel("addSubview:"), box);

        // Schedule removal on the UI thread after timeoutMs.
        TOAST_TIMER.schedule(() -> io.github.swat.Ui.invokeLater(() -> {
            try {
                Objc.sendVoid(box, Objc.sel("removeFromSuperview"));
                Objc.sendVoid(box, Objc.sel("release"));
            } catch (Throwable t) { t.printStackTrace(); }
        }), Math.max(500, timeoutMs), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Single shared scheduler used to dismiss toasts after their timeout. */
    private static final java.util.concurrent.ScheduledExecutorService TOAST_TIMER =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "swat-toast-timer");
            t.setDaemon(true);
            return t;
        });

    @Override
    public void onCloseRequest(BooleanSupplier permitClose) {
        this.permitClose = permitClose;
    }

    @Override
    public void close() {
        Delegates.WINDOW_SHOULD_CLOSE.remove(delegate.address());
        Delegates.WINDOW_DID_CLOSE.remove(delegate.address());
        Objc.sendVoid(window, Objc.sel("setDelegate:"), Objc.NIL);
        Objc.sendVoid(window, Objc.sel("close"));
        Objc.sendVoid(window, Objc.sel("release"));
        Objc.sendVoid(delegate, Objc.sel("release"));
    }
}
