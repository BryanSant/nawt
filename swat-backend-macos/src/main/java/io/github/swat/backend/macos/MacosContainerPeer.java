package io.github.swat.backend.macos;

import io.github.swat.spi.ContainerConfig;
import io.github.swat.spi.ContainerPeer;
import io.github.swat.spi.Orientation;
import io.github.swat.spi.Peer;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

final class MacosContainerPeer implements ContainerPeer {

    private static final MemoryLayout NSEDGE_INSETS = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("top"),
        ValueLayout.JAVA_DOUBLE.withName("left"),
        ValueLayout.JAVA_DOUBLE.withName("bottom"),
        ValueLayout.JAVA_DOUBLE.withName("right"));

    private final MemorySegment view; // NSStackView, retained
    private final boolean vertical;
    private final int padding;

    MacosContainerPeer(ContainerConfig cfg) {
        // [[NSStackView alloc] init]
        MemorySegment v = Objc.sendPtr(Objc.send_alloc(Objc.cls("NSStackView")), Objc.sel("init"));

        this.vertical = cfg.orientation() == Orientation.VERTICAL;
        this.padding = cfg.padding();
        // setOrientation: 0 = horizontal, 1 = vertical
        Objc.sendVoidLong(v, Objc.sel("setOrientation:"), vertical ? 1L : 0L);

        // Cross-axis alignment: Leading=5 for vertical (children left-aligned)
        // / Top=3 for horizontal. NSLayoutAttributeWidth (7) is documented as
        // "stretch children" but on macOS the actual behavior is right-align.
        // Combined with the per-child cross-axis width constraint added in
        // append(), this gives Column/Row "children fill cross-axis" semantics.
        long alignment = vertical ? 5L : 3L;
        Objc.sendVoidLong(v, Objc.sel("setAlignment:"), alignment);

        // setSpacing: (CGFloat)
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setSpacing:"), (double) cfg.spacing());
        } catch (Throwable t) { throw new RuntimeException(t); }

        // setEdgeInsets: NSEdgeInsets (4 doubles, by value)
        if (cfg.padding() > 0) {
            try (var arena = java.lang.foreign.Arena.ofConfined()) {
                MemorySegment insets = arena.allocate(NSEDGE_INSETS);
                double pad = cfg.padding();
                insets.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, pad);
                insets.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, pad);
                insets.setAtIndex(ValueLayout.JAVA_DOUBLE, 2, pad);
                insets.setAtIndex(ValueLayout.JAVA_DOUBLE, 3, pad);
                try {
                    Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, NSEDGE_INSETS))
                        .invoke(v, Objc.sel("setEdgeInsets:"), insets);
                } catch (Throwable t) { throw new RuntimeException(t); }
            }
        }

        // setTranslatesAutoresizingMaskIntoConstraints:NO — Auto Layout drives
        // this view's frame inside its parent.
        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // setDistribution: NSStackViewDistributionFill = 0.
        // Children stack at intrinsic size along the orientation axis. No
        // slack is generated because the document view inside ScrollContainer
        // is anchored at intrinsic height (top-pinned, no bottom pin). At
        // top-level, the window content view fills the window and one child
        // with low hugging priority absorbs slack — the typical layout idiom.
        Objc.sendVoidLong(v, Objc.sel("setDistribution:"), 0L);

        this.view = Objc.sendPtr(v, Objc.sel("retain"));
    }

    MemorySegment view() { return view; }

    @Override
    public void append(Peer child) {
        MemorySegment subview = peerView(child);
        addArrangedFillingCrossAxis(view, subview, vertical, padding);
    }

    /**
     * Add {@code subview} as an arranged subview of {@code stack} and pin its
     * cross-axis dimension (perpendicular to the stack's orientation) to the
     * stack's same dimension minus 2× edge inset. This gives Column/Row
     * "children fill the cross axis" semantics — without this constraint,
     * NSStackView's alignment pins the leading/top edge but lets children
     * collapse to intrinsic size, which would leave Splitter/Frame/Expander
     * etc. far narrower than the stack itself.
     *
     * The constraint is installed at priority 999 (just below required) so
     * widgets that explicitly set their content hugging priority to required
     * (1000) on the cross axis — Switch, Spinner, etc. — can opt out and
     * remain at their compact intrinsic width.
     */
    static void addArrangedFillingCrossAxis(MemorySegment stack, MemorySegment subview,
                                            boolean stackIsVertical, int edgeInset) {
        Objc.sendVoid(stack, Objc.sel("addArrangedSubview:"), subview);
        String dim = stackIsVertical ? "widthAnchor" : "heightAnchor";
        MemorySegment childAnchor = Objc.sendPtr(subview, Objc.sel(dim));
        MemorySegment stackAnchor = Objc.sendPtr(stack, Objc.sel(dim));
        double constant = -2.0 * edgeInset;
        MemorySegment c;
        try {
            if (constant == 0.0) {
                c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                    .invoke(childAnchor, Objc.sel("constraintEqualToAnchor:"), stackAnchor);
            } else {
                c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                        Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                    .invoke(childAnchor, Objc.sel("constraintEqualToAnchor:constant:"),
                        stackAnchor, constant);
            }
        } catch (Throwable t) { throw new RuntimeException(t); }
        // Set priority below required so the constraint doesn't propagate as
        // a hard requirement up to NSWindow's fittingSize, which would lock
        // the window's content width to the children's intrinsic widths.
        // Use KVC + NSNumber to set priority — direct setPriority:(float) via
        // FFM passes the float in the wrong register on Apple ARM64.
        setPriorityViaKVC(c, 350);
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    private static void setPriorityViaKVC(MemorySegment constraint, long priorityAsInt) {
        MemorySegment num;
        try {
            num = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT))
                .invoke(Objc.cls("NSNumber"), Objc.sel("numberWithInteger:"), priorityAsInt);
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(constraint, Objc.sel("setValue:forKey:"), num, NSString.from("priority"));
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    /**
     * Pin a view's content hugging priority along {@code orientation}
     * (0=horizontal, 1=vertical) to the given priority. Higher priorities
     * resist growing beyond intrinsic content size.
     */
    static void setContentHuggingPriority(MemorySegment view, int priority, long orientation) {
        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(
                    Objc.PTR, Objc.PTR, ValueLayout.JAVA_FLOAT, Objc.NSINT))
                .invoke(view, Objc.sel("setContentHuggingPriority:forOrientation:"),
                    (float) priority, orientation);
        } catch (Throwable t) { throw new RuntimeException(t); }
    }

    @Override
    public void close() {
        Objc.sendVoid(view, Objc.sel("release"));
    }

    static MemorySegment peerView(Peer p) {
        return switch (p) {
            case MacosLabelPeer lp -> lp.view();
            case MacosButtonPeer bp -> bp.view();
            case MacosTextFieldPeer tp -> tp.view();
            case MacosContainerPeer cp -> cp.view();
            case MacosListViewPeer lv -> lv.view();
            case MacosCheckboxPeer cb -> cb.view();
            case MacosSwitchPeer sw -> sw.view();
            case MacosRadioPeer rb -> rb.view();
            case MacosSliderPeer sl -> sl.view();
            case MacosProgressBarPeer pb -> pb.view();
            case MacosSpinnerPeer sp -> sp.view();
            case MacosDropDownPeer dd -> dd.view();
            case MacosFramePeer fp -> fp.view();
            case MacosScrollContainerPeer sc -> sc.view();
            case MacosTabsPeer tp -> tp.view();
            case MacosSplitterPeer spl -> spl.view();
            case MacosExpanderPeer ex -> ex.view();
            case MacosTreePeer tr -> tr.view();
            case MacosImagePeer im -> im.view();
            case MacosCanvasPeer cv -> cv.view();
            case MacosWindowPeer wp -> throw new IllegalArgumentException(
                "Window cannot be added as a child view");
            default -> throw new IllegalArgumentException(
                "Unknown peer type: " + p.getClass().getName());
        };
    }
}
