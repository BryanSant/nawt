package cc.nawt.backend.macos;

import cc.nawt.spi.NavigationSplitConfig;
import cc.nawt.spi.NavigationSplitPeer;
import cc.nawt.spi.Peer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;

/**
 * Sidebar+detail navigation split backed by {@code NSSplitViewController}.
 * The controller's two {@code NSSplitViewItem}s are configured with the
 * sidebar style on the leading pane (vibrant translucent background,
 * titlebar extension, auto-collapse behaviour, and a "toggle sidebar"
 * toolbar item when the parent window has an {@code NSToolbar}) and the
 * default content style on the trailing pane.
 *
 * <p>Because {@code NSSplitViewController} is itself an {@code NSViewController},
 * presenting it correctly in an {@code NSWindow} requires
 * {@code -[NSWindow setContentViewController:]}, not {@code setContentView:}.
 * {@link MacosWindowPeer#setContent} detects this peer type and routes through
 * the controller path so the window picks up the chrome that the split
 * controller offers.
 */
final class MacosNavigationSplitPeer implements NavigationSplitPeer {

    private final MemorySegment controller;       // NSSplitViewController, retained
    private final MemorySegment sidebarItem;      // NSSplitViewItem, retained
    private final MemorySegment detailItem;       // NSSplitViewItem, retained
    private final MemorySegment sidebarItemVC;    // NSViewController, retained — sidebar wrapper
    private MemorySegment detailItemVC;           // NSViewController, retained — detail wrapper (replaceable)

    MacosNavigationSplitPeer(NavigationSplitConfig cfg) {
        MemorySegment sidebarView = MacosContainerPeer.peerView(cfg.sidebar());
        MemorySegment detailView = MacosContainerPeer.peerView(cfg.detail());

        // [[NSSplitViewController alloc] init] (vendsAn NSSplitView via its view property).
        this.controller = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSSplitViewController")), Objc.sel("init"));

        // Wrap each pane in an NSViewController. Setting the view explicitly
        // before any access bypasses loadView/viewDidLoad's nib resolution.
        this.sidebarItemVC = newViewControllerHosting(sidebarView);
        this.detailItemVC = newViewControllerHosting(detailView);

        // +[NSSplitViewItem sidebarWithViewController:] — modern source-list
        // sidebar styling (added in 10.11). Auto-collapses on narrow windows
        // and registers a "toggle sidebar" toolbar item when the window has
        // an NSToolbar attached.
        this.sidebarItem = Objc.sendPtr(
            Objc.cls("NSSplitViewItem"),
            Objc.sel("sidebarWithViewController:"), sidebarItemVC);
        this.detailItem = Objc.sendPtr(
            Objc.cls("NSSplitViewItem"),
            Objc.sel("splitViewItemWithViewController:"), detailItemVC);

        // Retain the items so we can release explicitly in close().
        Objc.sendPtr(sidebarItem, Objc.sel("retain"));
        Objc.sendPtr(detailItem, Objc.sel("retain"));

        if (cfg.sidebarPreferredWidth() > 0) {
            applyCgFloat(sidebarItem, Objc.sel("setPreferredThicknessFraction:"), -1.0);
            applyCgFloat(sidebarItem, Objc.sel("setMinimumThickness:"),
                (double) (cfg.sidebarMinWidth() > 0 ? cfg.sidebarMinWidth() : cfg.sidebarPreferredWidth() / 2));
            // No direct "preferred width" API; the closest is setMaximumThickness +
            // setMinimumThickness combined. We just hint a min and let AppKit
            // pick a reasonable default starting size near it.
        } else if (cfg.sidebarMinWidth() > 0) {
            applyCgFloat(sidebarItem, Objc.sel("setMinimumThickness:"), (double) cfg.sidebarMinWidth());
        }

        Objc.sendVoid(controller, Objc.sel("addSplitViewItem:"), sidebarItem);
        Objc.sendVoid(controller, Objc.sel("addSplitViewItem:"), detailItem);

        // Retain the controller — NSWindow.setContentViewController: will retain
        // again, but explicit retain keeps us correct if the window outlives the
        // window peer or if setContentViewController: is never called.
        Objc.sendPtr(controller, Objc.sel("retain"));
    }

    /** Used by {@link MacosWindowPeer#setContent} to route through setContentViewController:. */
    MemorySegment viewController() { return controller; }

    /** Used by {@link MacosContainerPeer#peerView} when this peer is embedded as a child. */
    MemorySegment view() {
        return Objc.sendPtr(controller, Objc.sel("view"));
    }

    private static MemorySegment newViewControllerHosting(MemorySegment hostedView) {
        MemorySegment vc = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSViewController")), Objc.sel("init"));

        // Wrap the application's view in an autoresize-mask container so that
        // NSSplitViewController's traditional frame-based sizing reaches it.
        // Pinning our Auto-Layout view to fill that container lets the inner
        // NSStackView's constraint solver receive proper bounds — without
        // this wrapping, children get collapsed sizes despite required-priority
        // size constraints.
        MemorySegment container = Objc.sendPtr(
            Objc.send_alloc(Objc.cls("NSView")), Objc.sel("init"));
        Objc.sendVoidBool(container, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), true);
        // NSViewWidthSizable = 2, NSViewHeightSizable = 16
        Objc.sendVoidLong(container, Objc.sel("setAutoresizingMask:"), 2L | 16L);

        Objc.sendVoid(container, Objc.sel("addSubview:"), hostedView);
        pinEqual(hostedView, "leadingAnchor",  container, "leadingAnchor");
        pinEqual(hostedView, "trailingAnchor", container, "trailingAnchor");
        pinEqual(hostedView, "topAnchor",      container, "topAnchor");
        pinEqual(hostedView, "bottomAnchor",   container, "bottomAnchor");

        Objc.sendVoid(vc, Objc.sel("setView:"), container);
        return Objc.sendPtr(vc, Objc.sel("retain"));
    }

    private static void pinEqual(MemorySegment a, String aAnchor, MemorySegment b, String bAnchor) {
        MemorySegment anchorA = Objc.sendPtr(a, Objc.sel(aAnchor));
        MemorySegment anchorB = Objc.sendPtr(b, Objc.sel(bAnchor));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(anchorA, Objc.sel("constraintEqualToAnchor:"), anchorB);
        } catch (Throwable t) { throw new RuntimeException(t); }
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    private static void applyCgFloat(MemorySegment target, MemorySegment sel, double value) {
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(target, sel, value);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override
    public void setSidebar(Peer sidebar) {
        if (sidebar == null) return;
        replaceHostedView(sidebarItemVC, MacosContainerPeer.peerView(sidebar));
    }

    @Override
    public void setDetail(Peer detail) {
        if (detail == null) return;
        replaceHostedView(detailItemVC, MacosContainerPeer.peerView(detail));
    }

    /**
     * Swap the application view inside the controller's autoresize-mask
     * container (set up by {@link #newViewControllerHosting}). Keeps the
     * controller's outer view stable so split-view-controller layout state
     * (divider position, sidebar collapse state) survives the swap.
     */
    private static void replaceHostedView(MemorySegment vc, MemorySegment newHostedView) {
        MemorySegment container = Objc.sendPtr(vc, Objc.sel("view"));
        // Remove every existing subview (typically just the previous hosted view).
        MemorySegment subviews = Objc.sendPtr(container, Objc.sel("subviews"));
        long count = Objc.sendLong(subviews, Objc.sel("count"));
        for (long i = 0; i < count; i++) {
            MemorySegment sv;
            try {
                sv = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSUINT))
                    .invoke(subviews, Objc.sel("objectAtIndex:"), 0L);
            } catch (Throwable t) { throw new RuntimeException(t); }
            // Re-fetch index 0 each iteration since removeFromSuperview shifts indices.
            Objc.sendVoid(sv, Objc.sel("removeFromSuperview"));
            subviews = Objc.sendPtr(container, Objc.sel("subviews"));
        }
        Objc.sendVoid(container, Objc.sel("addSubview:"), newHostedView);
        pinEqual(newHostedView, "leadingAnchor",  container, "leadingAnchor");
        pinEqual(newHostedView, "trailingAnchor", container, "trailingAnchor");
        pinEqual(newHostedView, "topAnchor",      container, "topAnchor");
        pinEqual(newHostedView, "bottomAnchor",   container, "bottomAnchor");
    }

    @Override
    public void close() {
        Objc.sendVoid(sidebarItem, Objc.sel("release"));
        Objc.sendVoid(detailItem, Objc.sel("release"));
        Objc.sendVoid(sidebarItemVC, Objc.sel("release"));
        Objc.sendVoid(detailItemVC, Objc.sel("release"));
        Objc.sendVoid(controller, Objc.sel("release"));
    }
}
