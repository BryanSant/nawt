package cc.nawt.backend.macos;

import cc.nawt.spi.Alignment;
import cc.nawt.spi.ChildLayoutConfig;
import cc.nawt.spi.ContainerConfig;
import cc.nawt.spi.ContainerPeer;
import cc.nawt.spi.Orientation;
import cc.nawt.spi.Peer;

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
    private final Alignment crossAxis;
    private MemorySegment firstExpandChild; // for pinning subsequent expand siblings equal

    MacosContainerPeer(ContainerConfig cfg) {
        MemorySegment v = Objc.sendPtr(Objc.send_alloc(Objc.cls("NSStackView")), Objc.sel("init"));

        this.vertical = cfg.orientation() == Orientation.VERTICAL;
        this.padding = cfg.padding();
        this.crossAxis = cfg.crossAxis();
        Objc.sendVoidLong(v, Objc.sel("setOrientation:"), vertical ? 1L : 0L);

        // NSStackView's positional cross-axis alignment is set once for the
        // whole stack. We map the container's crossAxis to the closest
        // NSLayoutAttribute. The per-child STRETCH/non-STRETCH split is then
        // handled in append() by conditionally adding the cross-axis stretch
        // constraint — see LAYOUT.md for the contract.
        Objc.sendVoidLong(v, Objc.sel("setAlignment:"), stackAlignmentFor(vertical, crossAxis));

        try {
            Objc.msgSend(FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.CGFLOAT))
                .invoke(v, Objc.sel("setSpacing:"), (double) cfg.spacing());
        } catch (Throwable t) { throw new RuntimeException(t); }

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

        Objc.sendVoidBool(v, Objc.sel("setTranslatesAutoresizingMaskIntoConstraints:"), false);

        // NSStackViewDistributionFill (0): children stack at intrinsic size
        // along the main axis; slack flows to children with the lowest hugging
        // priority — which is exactly how ChildLayoutConfig.expand works.
        Objc.sendVoidLong(v, Objc.sel("setDistribution:"), 0L);

        this.view = Objc.sendPtr(v, Objc.sel("retain"));
    }

    MemorySegment view() { return view; }

    @Override
    public void append(Peer child) {
        append(child, ChildLayoutConfig.DEFAULT);
    }

    @Override
    public void append(Peer child, ChildLayoutConfig hints) {
        MemorySegment subview = peerView(child);
        Alignment effective = hints.alignSelf() != null ? hints.alignSelf() : crossAxis;

        Objc.sendVoid(view, Objc.sel("addArrangedSubview:"), subview);

        if (effective == Alignment.STRETCH) {
            addCrossAxisStretchConstraint(view, subview, vertical, padding);
        }

        if (hints.expand()) {
            // Lower hugging priority along the main axis below the default
            // (250) so NSStackViewDistributionFill routes slack to this child.
            // NSLayoutConstraintOrientation: 0 = horizontal (width hugging),
            // 1 = vertical (height hugging). Main axis = stack orientation.
            long mainAxisOrientation = vertical ? 1L : 0L;
            setContentHuggingPriority(subview, 100, mainAxisOrientation);

            // With identical hugging priorities, NSStackViewDistributionFill
            // gives all slack to the first expanding child. Pin each subsequent
            // expanding sibling's main-axis dimension equal to the first one
            // so they share slack equally — matching the LAYOUT.md contract
            // and GTK's hexpand semantics.
            if (firstExpandChild == null) {
                firstExpandChild = subview;
            } else {
                pinMainAxisEqual(firstExpandChild, subview, vertical);
            }
        }
    }

    /**
     * Map an {@link Alignment} to the NSLayoutAttribute used by
     * {@code NSStackView.setAlignment:} for positional cross-axis alignment.
     * STRETCH maps to a leading-edge anchor because the actual stretching is
     * applied per-child via {@link #addCrossAxisStretchConstraint}.
     */
    private static long stackAlignmentFor(boolean vertical, Alignment cross) {
        if (vertical) {
            return switch (cross) {
                case STRETCH, START -> 5L; // NSLayoutAttributeLeading
                case CENTER, BASELINE -> 9L; // NSLayoutAttributeCenterX
                case END -> 6L; // NSLayoutAttributeTrailing
            };
        }
        return switch (cross) {
            case STRETCH, START -> 3L; // NSLayoutAttributeTop
            case CENTER -> 10L; // NSLayoutAttributeCenterY
            case END -> 4L; // NSLayoutAttributeBottom
            case BASELINE -> 12L; // NSLayoutAttributeFirstBaseline
        };
    }

    /**
     * Convenience for internal peers (Expander etc.) that build their own
     * NSStackViews and want the "STRETCH" behavior without going through the
     * {@link ContainerPeer#append} path. Adds the subview and pins it
     * cross-axis. Equivalent to the original {@code addArrangedFillingCrossAxis}.
     */
    static void addArrangedFillingCrossAxis(MemorySegment stack, MemorySegment subview,
                                            boolean stackIsVertical, int edgeInset) {
        Objc.sendVoid(stack, Objc.sel("addArrangedSubview:"), subview);
        addCrossAxisStretchConstraint(stack, subview, stackIsVertical, edgeInset);
    }

    /**
     * Pin the child's cross-axis dimension to the stack's same dimension
     * (minus 2× edge inset). Priority 350 keeps the constraint sub-required
     * so it doesn't propagate to NSWindow's fittingSize and lock the window
     * width to the children's intrinsic widths.
     *
     * <p>KVC + NSNumber is used to set the priority because direct
     * {@code setPriority:(float)} via FFM passes the float in the wrong
     * register on Apple ARM64.
     */
    static void addCrossAxisStretchConstraint(MemorySegment stack, MemorySegment subview,
                                              boolean stackIsVertical, int edgeInset) {
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
        setPriorityViaKVC(c, 350);
        Objc.sendVoidBool(c, Objc.sel("setActive:"), true);
    }

    /**
     * Pin two arranged subviews to have equal main-axis size. Used to make
     * multiple expand-marked siblings share slack equally under
     * NSStackViewDistributionFill. Priority 700 — below required so it
     * doesn't propagate to the window's fittingSize and lock its width, but
     * above the 350 cross-axis stretch so equal-distribution wins when it
     * meaningfully constrains the layout.
     */
    static void pinMainAxisEqual(MemorySegment a, MemorySegment b, boolean stackIsVertical) {
        String dim = stackIsVertical ? "heightAnchor" : "widthAnchor";
        MemorySegment anchorA = Objc.sendPtr(a, Objc.sel(dim));
        MemorySegment anchorB = Objc.sendPtr(b, Objc.sel(dim));
        MemorySegment c;
        try {
            c = (MemorySegment) Objc.msgSend(FunctionDescriptor.of(
                    Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR))
                .invoke(anchorB, Objc.sel("constraintEqualToAnchor:"), anchorA);
        } catch (Throwable t) { throw new RuntimeException(t); }
        setPriorityViaKVC(c, 700);
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
     * (0=horizontal, 1=vertical). Higher priorities resist growing beyond
     * intrinsic content size; lower priorities make the view absorb slack
     * preferentially under NSStackViewDistributionFill.
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
            case MacosGridPeer gp -> gp.view();
            case MacosTreePeer tr -> tr.view();
            case MacosImagePeer im -> im.view();
            case MacosCanvasPeer cv -> cv.view();
            case MacosSidebarPeer sb -> sb.view();
            case MacosNavigationSplitPeer ns -> ns.view();
            case MacosMapPeer mp -> mp.view();
            case MacosOverlayPeer ov -> ov.view();
            case MacosDividerPeer dv -> dv.view();
            case MacosWindowPeer wp -> throw new IllegalArgumentException(
                "Window cannot be added as a child view");
            default -> throw new IllegalArgumentException(
                "Unknown peer type: " + p.getClass().getName());
        };
    }
}
