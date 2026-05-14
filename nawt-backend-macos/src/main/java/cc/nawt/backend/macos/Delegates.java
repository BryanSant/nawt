package cc.nawt.backend.macos;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Custom Objective-C delegate/target classes used by widget peers. Classes are
 * allocated and registered once at class load; per-instance state lives in
 * Java-side {@link ConcurrentHashMap}s keyed by the delegate object's pointer.
 */
final class Delegates {
    private Delegates() {}

    /* ---------- Button target (target/action click handling) ---------- */

    static final MemorySegment BUTTON_TARGET_CLASS;
    static final MemorySegment BUTTON_ACTION_SEL = Objc.sel("nawtClick:");
    static final ConcurrentHashMap<Long, Runnable> BUTTON_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- Window delegate (shouldClose / willClose) ---------- */

    static final MemorySegment WINDOW_DELEGATE_CLASS;
    static final ConcurrentHashMap<Long, BooleanSupplier> WINDOW_SHOULD_CLOSE = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Long, Runnable> WINDOW_DID_CLOSE = new ConcurrentHashMap<>();

    /* ---------- Text field delegate (controlTextDidChange:) ---------- */

    static final MemorySegment TEXTFIELD_DELEGATE_CLASS;
    static final ConcurrentHashMap<Long, Runnable> TEXTFIELD_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- Menu action target (target/action menu activation) ---------- */

    static final MemorySegment MENU_TARGET_CLASS;
    static final MemorySegment MENU_ACTION_SEL = Objc.sel("nawtMenuClick:");
    static final ConcurrentHashMap<Long, Runnable> MENU_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- App-menu About target ----------
     * The app menu's About item is wired to a single shared target; replacing
     * the handler is a volatile assignment so {@code Toolkit.onAbout(...)}
     * applies retroactively to an already-installed menu. */

    static final MemorySegment APP_ABOUT_TARGET_CLASS;
    static final MemorySegment APP_ABOUT_ACTION_SEL = Objc.sel("nawtAppAbout:");
    static volatile Runnable APP_ABOUT_HANDLER;

    /* ---------- Table view data source/delegate ---------- */

    static final MemorySegment TABLE_DATASOURCE_CLASS;
    static final ConcurrentHashMap<Long, java.util.function.IntFunction<String>> TABLE_VALUE_PROVIDERS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Long, java.util.function.IntSupplier> TABLE_ROW_COUNT_PROVIDERS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Long, Runnable> TABLE_SELECTION_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- Toggle target (checkbox / switch / radio target/action) ---------- */

    static final MemorySegment TOGGLE_TARGET_CLASS;
    static final MemorySegment TOGGLE_ACTION_SEL = Objc.sel("nawtToggle:");
    static final ConcurrentHashMap<Long, Runnable> TOGGLE_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- Tabs delegate (NSTabView tabView:didSelectTabViewItem:) ---------- */

    static final MemorySegment TABS_DELEGATE_CLASS;
    static final ConcurrentHashMap<Long, Runnable> TABS_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- Canvas (NSView subclass with drawRect:) ---------- */

    static final MemorySegment CANVAS_VIEW_CLASS;
    /** Per-instance handler taking a CGContextRef. */
    static final ConcurrentHashMap<Long, java.util.function.Consumer<MemorySegment>> CANVAS_HANDLERS =
        new ConcurrentHashMap<>();

    /* ---------- Outline view data source/delegate ----------
     * Items are NSString path keys: "" = the user's root node,
     * "0" = root.children[0], "0.1" = root.children[0].children[1], ...
     * Resolver returns the {@link cc.nawt.spi.TreeNodeData} for a path
     * string, or null if the path is invalid. */

    static final MemorySegment OUTLINE_DATASOURCE_CLASS;
    static final ConcurrentHashMap<Long, java.util.function.Function<String, cc.nawt.spi.TreeNodeData>>
        OUTLINE_RESOLVERS = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Long, Runnable> OUTLINE_SELECTION_HANDLERS = new ConcurrentHashMap<>();

    /* ---------- Toolbar delegate (NSToolbar for HeaderBar) ----------
     * Per-instance lists of start- and end-region item NSViews; the delegate
     * synthesises NSToolbarItem instances on demand keyed by identifier
     * "nawt.start.<n>" / "nawt.end.<n>". Flexible space between the regions
     * is the built-in NSToolbarFlexibleSpaceItem. */

    static final MemorySegment TOOLBAR_DELEGATE_CLASS;
    static final ConcurrentHashMap<Long, java.util.List<MemorySegment>> TOOLBAR_START_VIEWS =
        new ConcurrentHashMap<>();
    static final ConcurrentHashMap<Long, java.util.List<MemorySegment>> TOOLBAR_END_VIEWS =
        new ConcurrentHashMap<>();

    static {
        BUTTON_TARGET_CLASS = registerButtonTargetClass();
        WINDOW_DELEGATE_CLASS = registerWindowDelegateClass();
        TEXTFIELD_DELEGATE_CLASS = registerTextFieldDelegateClass();
        MENU_TARGET_CLASS = registerMenuTargetClass();
        APP_ABOUT_TARGET_CLASS = registerAppAboutTargetClass();
        TABLE_DATASOURCE_CLASS = registerTableDataSourceClass();
        TOGGLE_TARGET_CLASS = registerToggleTargetClass();
        TABS_DELEGATE_CLASS = registerTabsDelegateClass();
        CANVAS_VIEW_CLASS = registerCanvasViewClass();
        OUTLINE_DATASOURCE_CLASS = registerOutlineDataSourceClass();
        TOOLBAR_DELEGATE_CLASS = registerToolbarDelegateClass();
    }

    static MemorySegment newOutlineDataSource() {
        return Objc.sendPtr(Objc.send_alloc(OUTLINE_DATASOURCE_CLASS), Objc.sel("init"));
    }

    static MemorySegment newToolbarDelegate() {
        return Objc.sendPtr(Objc.send_alloc(TOOLBAR_DELEGATE_CLASS), Objc.sel("init"));
    }

    static MemorySegment newToggleTarget() {
        return Objc.sendPtr(Objc.send_alloc(TOGGLE_TARGET_CLASS), Objc.sel("init"));
    }

    static MemorySegment newTabsDelegate() {
        return Objc.sendPtr(Objc.send_alloc(TABS_DELEGATE_CLASS), Objc.sel("init"));
    }

    static MemorySegment newCanvasView() {
        return Objc.sendPtr(Objc.send_alloc(CANVAS_VIEW_CLASS), Objc.sel("init"));
    }

    static MemorySegment newButtonTarget() {
        MemorySegment instance = Objc.sendPtr(
            Objc.send_alloc(BUTTON_TARGET_CLASS), Objc.sel("init"));
        return instance;
    }

    static MemorySegment newWindowDelegate() {
        return Objc.sendPtr(Objc.send_alloc(WINDOW_DELEGATE_CLASS), Objc.sel("init"));
    }

    static MemorySegment newTextFieldDelegate() {
        return Objc.sendPtr(Objc.send_alloc(TEXTFIELD_DELEGATE_CLASS), Objc.sel("init"));
    }

    static MemorySegment newMenuTarget() {
        return Objc.sendPtr(Objc.send_alloc(MENU_TARGET_CLASS), Objc.sel("init"));
    }

    static MemorySegment newAppAboutTarget() {
        return Objc.sendPtr(Objc.send_alloc(APP_ABOUT_TARGET_CLASS), Objc.sel("init"));
    }

    static MemorySegment newTableDataSource() {
        return Objc.sendPtr(Objc.send_alloc(TABLE_DATASOURCE_CLASS), Objc.sel("init"));
    }

    /* ---------- Class registrations ---------- */

    private static MemorySegment registerButtonTargetClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtButtonTarget");
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                Delegates.class, "buttonClick",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment imp = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            // type encoding: v@:@ — void return, id self, SEL _cmd, id sender
            Objc.addMethod(cls, BUTTON_ACTION_SEL, imp, "v@:@");
            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused") // invoked via FFM upcall stub
    private static void buttonClick(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        Runnable r = BUTTON_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    private static MemorySegment registerWindowDelegateClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtWindowDelegate");
        try {
            // windowShouldClose: -> BOOL
            MethodHandle shouldCloseMh = MethodHandles.lookup().findStatic(
                Delegates.class, "windowShouldClose",
                MethodType.methodType(boolean.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment shouldCloseImp = Linker.nativeLinker().upcallStub(
                shouldCloseMh,
                FunctionDescriptor.of(Objc.BOOL, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("windowShouldClose:"), shouldCloseImp, "B@:@");

            // windowWillClose: -> void
            MethodHandle willCloseMh = MethodHandles.lookup().findStatic(
                Delegates.class, "windowWillClose",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment willCloseImp = Linker.nativeLinker().upcallStub(
                willCloseMh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("windowWillClose:"), willCloseImp, "v@:@");

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static boolean windowShouldClose(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        BooleanSupplier h = WINDOW_SHOULD_CLOSE.get(self.address());
        if (h == null) return true;
        try { return h.getAsBoolean(); }
        catch (Throwable t) { t.printStackTrace(); return true; }
    }

    @SuppressWarnings("unused")
    private static void windowWillClose(MemorySegment self, MemorySegment cmd, MemorySegment notification) {
        Runnable r = WINDOW_DID_CLOSE.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    private static MemorySegment registerTextFieldDelegateClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtTextFieldDelegate");
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                Delegates.class, "controlTextDidChange",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment imp = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("controlTextDidChange:"), imp, "v@:@");
            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void controlTextDidChange(MemorySegment self, MemorySegment cmd, MemorySegment notification) {
        Runnable r = TEXTFIELD_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /* ---------- Menu target ---------- */

    private static MemorySegment registerMenuTargetClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtMenuActionTarget");
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                Delegates.class, "menuClick",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment imp = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, MENU_ACTION_SEL, imp, "v@:@");
            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void menuClick(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        Runnable r = MENU_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /* ---------- App-menu About target ---------- */

    private static MemorySegment registerAppAboutTargetClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtAppAboutTarget");
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                Delegates.class, "appAboutClick",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment imp = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, APP_ABOUT_ACTION_SEL, imp, "v@:@");
            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /** Invoked from AppKit when the About menu item is picked. Runs the
     *  current {@link #APP_ABOUT_HANDLER} on a fresh virtual thread so
     *  blocking UI calls like {@code MessageDialog.show()} are allowed,
     *  mirroring how button {@code onClick} handlers dispatch. Falls back
     *  to the standard system About panel when no handler is registered. */
    @SuppressWarnings("unused")
    private static void appAboutClick(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        Runnable r = APP_ABOUT_HANDLER;
        if (r != null) {
            Thread.startVirtualThread(() -> {
                try { r.run(); }
                catch (Throwable t) { t.printStackTrace(); }
            });
            return;
        }
        MemorySegment app = Objc.sendPtr(Objc.cls("NSApplication"), Objc.sel("sharedApplication"));
        Objc.sendVoid(app, Objc.sel("orderFrontStandardAboutPanel:"), Objc.NIL);
    }

    /* ---------- Table data source/delegate (combined into one class) ---------- */

    private static MemorySegment registerTableDataSourceClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtTableDataSource");
        try {
            // -(NSInteger)numberOfRowsInTableView:(NSTableView*)tv  → encoding "q@:@"
            MethodHandle rowsMh = MethodHandles.lookup().findStatic(
                Delegates.class, "numberOfRowsInTableView",
                MethodType.methodType(long.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment rowsImp = Linker.nativeLinker().upcallStub(
                rowsMh,
                FunctionDescriptor.of(Objc.NSINT, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("numberOfRowsInTableView:"), rowsImp, "q@:@");

            // -(id)tableView:(NSTableView*)tv objectValueForTableColumn:(NSTableColumn*)col row:(NSInteger)row → "@@:@@q"
            MethodHandle valMh = MethodHandles.lookup().findStatic(
                Delegates.class, "objectValueForTableColumnRow",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class, MemorySegment.class, long.class));
            MemorySegment valImp = Linker.nativeLinker().upcallStub(
                valMh,
                FunctionDescriptor.of(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("tableView:objectValueForTableColumn:row:"), valImp, "@@:@@q");

            // -(void)tableViewSelectionDidChange:(NSNotification*)n → "v@:@"
            MethodHandle selMh = MethodHandles.lookup().findStatic(
                Delegates.class, "tableViewSelectionDidChange",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment selImp = Linker.nativeLinker().upcallStub(
                selMh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("tableViewSelectionDidChange:"), selImp, "v@:@");

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static long numberOfRowsInTableView(MemorySegment self, MemorySegment cmd, MemorySegment tv) {
        java.util.function.IntSupplier s = TABLE_ROW_COUNT_PROVIDERS.get(self.address());
        if (s == null) return 0;
        try { return s.getAsInt(); }
        catch (Throwable t) { t.printStackTrace(); return 0; }
    }

    @SuppressWarnings("unused")
    private static MemorySegment objectValueForTableColumnRow(
        MemorySegment self, MemorySegment cmd, MemorySegment tv, MemorySegment col, long row) {
        java.util.function.IntFunction<String> p = TABLE_VALUE_PROVIDERS.get(self.address());
        if (p == null) return MemorySegment.NULL;
        try {
            String value = p.apply((int) row);
            if (value == null) return MemorySegment.NULL;
            return NSString.from(value);
        } catch (Throwable t) {
            t.printStackTrace();
            return MemorySegment.NULL;
        }
    }

    @SuppressWarnings("unused")
    private static void tableViewSelectionDidChange(MemorySegment self, MemorySegment cmd, MemorySegment notification) {
        Runnable r = TABLE_SELECTION_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /* ---------- Toggle target (checkbox / switch / radio) ---------- */

    private static MemorySegment registerToggleTargetClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtToggleTarget");
        try {
            MethodHandle mh = MethodHandles.lookup().findStatic(
                Delegates.class, "toggleClick",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment imp = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, TOGGLE_ACTION_SEL, imp, "v@:@");
            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void toggleClick(MemorySegment self, MemorySegment cmd, MemorySegment sender) {
        Runnable r = TOGGLE_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /* ---------- Tabs delegate ---------- */

    private static MemorySegment registerTabsDelegateClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtTabsDelegate");
        try {
            // -(void)tabView:(NSTabView*)tv didSelectTabViewItem:(NSTabViewItem*)item → "v@:@@"
            MethodHandle mh = MethodHandles.lookup().findStatic(
                Delegates.class, "tabViewDidSelect",
                MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class));
            MemorySegment imp = Linker.nativeLinker().upcallStub(
                mh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("tabView:didSelectTabViewItem:"), imp, "v@:@@");
            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void tabViewDidSelect(MemorySegment self, MemorySegment cmd,
                                         MemorySegment tabView, MemorySegment item) {
        Runnable r = TABS_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /* ---------- Canvas view ---------- */

    private static MemorySegment registerCanvasViewClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSView"), "NawtCanvasView");
        try {
            // -(void)drawRect:(NSRect)dirtyRect → "v@:" + 4 doubles for NSRect
            MethodHandle drawMh = MethodHandles.lookup().findStatic(
                Delegates.class, "canvasDrawRect",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment drawImp = Linker.nativeLinker().upcallStub(
                drawMh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, CG.CGRECT),
                Objc.GLOBAL);
            // Type encoding for NSRect (struct of 4 CGFloat). On 64-bit this is "{CGRect={CGPoint=dd}{CGSize=dd}}"
            // but Cocoa often accepts "{CGRect={CGPoint=dd}{CGSize=dd}}@8@16" or simpler. Many implementations use "v@:" prefix only.
            Objc.addMethod(cls, Objc.sel("drawRect:"), drawImp,
                "v@:{CGRect={CGPoint=dd}{CGSize=dd}}");

            // -(BOOL)isFlipped → "B@:"
            MethodHandle flipMh = MethodHandles.lookup().findStatic(
                Delegates.class, "canvasIsFlipped",
                MethodType.methodType(boolean.class, MemorySegment.class, MemorySegment.class));
            MemorySegment flipImp = Linker.nativeLinker().upcallStub(
                flipMh,
                FunctionDescriptor.of(Objc.BOOL, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("isFlipped"), flipImp, "B@:");

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static void canvasDrawRect(MemorySegment self, MemorySegment cmd, MemorySegment dirtyRect) {
        java.util.function.Consumer<MemorySegment> h = CANVAS_HANDLERS.get(self.address());
        if (h == null) return;
        // Get current CGContext: [[NSGraphicsContext currentContext] CGContext]
        MemorySegment nsGfx = Objc.sendPtr(Objc.cls("NSGraphicsContext"), Objc.sel("currentContext"));
        if (nsGfx.address() == 0) return;
        MemorySegment ctx = Objc.sendPtr(nsGfx, Objc.sel("CGContext"));
        if (ctx.address() == 0) return;
        try { h.accept(ctx); }
        catch (Throwable t) { t.printStackTrace(); }
    }

    @SuppressWarnings("unused")
    private static boolean canvasIsFlipped(MemorySegment self, MemorySegment cmd) {
        return true; // top-left origin
    }

    /* ---------- Outline view data source/delegate ---------- */

    private static MemorySegment registerOutlineDataSourceClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtOutlineDataSource");
        try {
            // -(NSInteger)outlineView:(NSOutlineView*)ov numberOfChildrenOfItem:(id)item → "q@:@@"
            MethodHandle nMh = MethodHandles.lookup().findStatic(
                Delegates.class, "outlineNumberOfChildren",
                MethodType.methodType(long.class,
                    MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class));
            MemorySegment nImp = Linker.nativeLinker().upcallStub(
                nMh,
                FunctionDescriptor.of(Objc.NSINT, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("outlineView:numberOfChildrenOfItem:"), nImp, "q@:@@");

            // -(BOOL)outlineView:(NSOutlineView*)ov isItemExpandable:(id)item → "B@:@@"
            MethodHandle expMh = MethodHandles.lookup().findStatic(
                Delegates.class, "outlineIsItemExpandable",
                MethodType.methodType(boolean.class,
                    MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class));
            MemorySegment expImp = Linker.nativeLinker().upcallStub(
                expMh,
                FunctionDescriptor.of(Objc.BOOL, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("outlineView:isItemExpandable:"), expImp, "B@:@@");

            // -(id)outlineView:(NSOutlineView*)ov child:(NSInteger)idx ofItem:(id)item → "@@:@q@"
            MethodHandle chMh = MethodHandles.lookup().findStatic(
                Delegates.class, "outlineChild",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, long.class, MemorySegment.class));
            MemorySegment chImp = Linker.nativeLinker().upcallStub(
                chMh,
                FunctionDescriptor.of(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.NSINT, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("outlineView:child:ofItem:"), chImp, "@@:@q@");

            // -(id)outlineView:(NSOutlineView*)ov objectValueForTableColumn:(NSTableColumn*)col byItem:(id)item → "@@:@@@"
            MethodHandle valMh = MethodHandles.lookup().findStatic(
                Delegates.class, "outlineObjectValue",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment valImp = Linker.nativeLinker().upcallStub(
                valMh,
                FunctionDescriptor.of(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls,
                Objc.sel("outlineView:objectValueForTableColumn:byItem:"), valImp, "@@:@@@");

            // -(void)outlineViewSelectionDidChange:(NSNotification*)n → "v@:@"
            MethodHandle selMh = MethodHandles.lookup().findStatic(
                Delegates.class, "outlineSelectionDidChange",
                MethodType.methodType(void.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment selImp = Linker.nativeLinker().upcallStub(
                selMh,
                FunctionDescriptor.ofVoid(Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("outlineViewSelectionDidChange:"), selImp, "v@:@");

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static cc.nawt.spi.TreeNodeData resolveOutlineNode(
            MemorySegment self, MemorySegment item) {
        var resolver = OUTLINE_RESOLVERS.get(self.address());
        if (resolver == null) return null;
        String key = (item == null || item.address() == 0) ? null : NSString.toJava(item);
        if (key == null) key = "";
        return resolver.apply(key);
    }

    @SuppressWarnings("unused")
    private static long outlineNumberOfChildren(
            MemorySegment self, MemorySegment cmd, MemorySegment ov, MemorySegment item) {
        try {
            // nil item → outline root: surface a single child (the user's tree root).
            if (item == null || item.address() == 0) {
                var resolver = OUTLINE_RESOLVERS.get(self.address());
                return (resolver != null && resolver.apply("") != null) ? 1L : 0L;
            }
            cc.nawt.spi.TreeNodeData node = resolveOutlineNode(self, item);
            return node == null ? 0L : node.children().size();
        } catch (Throwable t) { t.printStackTrace(); return 0L; }
    }

    @SuppressWarnings("unused")
    private static boolean outlineIsItemExpandable(
            MemorySegment self, MemorySegment cmd, MemorySegment ov, MemorySegment item) {
        try {
            cc.nawt.spi.TreeNodeData node = resolveOutlineNode(self, item);
            return node != null && !node.children().isEmpty();
        } catch (Throwable t) { t.printStackTrace(); return false; }
    }

    @SuppressWarnings("unused")
    private static MemorySegment outlineChild(
            MemorySegment self, MemorySegment cmd, MemorySegment ov, long idx, MemorySegment item) {
        try {
            // child 0 of the outline root is the user's tree root, keyed as "".
            if (item == null || item.address() == 0) {
                return NSString.from("");
            }
            String parentKey = NSString.toJava(item);
            String childKey = parentKey.isEmpty()
                ? Long.toString(idx)
                : parentKey + "." + idx;
            return NSString.from(childKey);
        } catch (Throwable t) { t.printStackTrace(); return MemorySegment.NULL; }
    }

    @SuppressWarnings("unused")
    private static MemorySegment outlineObjectValue(
            MemorySegment self, MemorySegment cmd, MemorySegment ov,
            MemorySegment col, MemorySegment item) {
        try {
            cc.nawt.spi.TreeNodeData node = resolveOutlineNode(self, item);
            if (node == null) return MemorySegment.NULL;
            return NSString.from(node.label());
        } catch (Throwable t) { t.printStackTrace(); return MemorySegment.NULL; }
    }

    @SuppressWarnings("unused")
    private static void outlineSelectionDidChange(
            MemorySegment self, MemorySegment cmd, MemorySegment notification) {
        Runnable r = OUTLINE_SELECTION_HANDLERS.get(self.address());
        if (r != null) {
            try { r.run(); }
            catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /* ---------- Toolbar delegate ---------- */

    /** {@code NSToolbarFlexibleSpaceItem} identifier — built-in to AppKit. */
    static final String TOOLBAR_FLEXIBLE_SPACE_ID = "NSToolbarFlexibleSpaceItem";
    static final String TOOLBAR_START_PREFIX = "nawt.start.";
    static final String TOOLBAR_END_PREFIX = "nawt.end.";

    private static MemorySegment registerToolbarDelegateClass() {
        MemorySegment cls = Objc.allocateClassPair(Objc.cls("NSObject"), "NawtToolbarDelegate");
        try {
            // -(NSArray*)toolbarAllowedItemIdentifiers:(NSToolbar*)tb → "@@:@"
            MethodHandle allowedMh = MethodHandles.lookup().findStatic(
                Delegates.class, "toolbarAllowedItemIdentifiers",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, MemorySegment.class));
            MemorySegment allowedImp = Linker.nativeLinker().upcallStub(
                allowedMh,
                FunctionDescriptor.of(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR),
                Objc.GLOBAL);
            Objc.addMethod(cls, Objc.sel("toolbarAllowedItemIdentifiers:"), allowedImp, "@@:@");
            Objc.addMethod(cls, Objc.sel("toolbarDefaultItemIdentifiers:"), allowedImp, "@@:@");

            // -(NSToolbarItem*)toolbar:(NSToolbar*)tb itemForItemIdentifier:(NSString*)id
            //     willBeInsertedIntoToolbar:(BOOL)flag → "@@:@@B"
            MethodHandle itemMh = MethodHandles.lookup().findStatic(
                Delegates.class, "toolbarItemForIdentifier",
                MethodType.methodType(MemorySegment.class,
                    MemorySegment.class, MemorySegment.class,
                    MemorySegment.class, MemorySegment.class, boolean.class));
            MemorySegment itemImp = Linker.nativeLinker().upcallStub(
                itemMh,
                FunctionDescriptor.of(Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.PTR, Objc.BOOL),
                Objc.GLOBAL);
            Objc.addMethod(cls,
                Objc.sel("toolbar:itemForItemIdentifier:willBeInsertedIntoToolbar:"),
                itemImp, "@@:@@B");

            Objc.registerClassPair(cls);
            return cls;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    private static MemorySegment toolbarAllowedItemIdentifiers(
            MemorySegment self, MemorySegment cmd, MemorySegment toolbar) {
        try {
            java.util.List<MemorySegment> start = TOOLBAR_START_VIEWS.get(self.address());
            java.util.List<MemorySegment> end = TOOLBAR_END_VIEWS.get(self.address());
            // Build [NSMutableArray array]; addObject: each identifier; return.
            MemorySegment arr = Objc.sendPtr(
                Objc.cls("NSMutableArray"), Objc.sel("array"));
            int startN = start == null ? 0 : start.size();
            int endN = end == null ? 0 : end.size();
            for (int i = 0; i < startN; i++) {
                Objc.sendVoid(arr, Objc.sel("addObject:"),
                    NSString.from(TOOLBAR_START_PREFIX + i));
            }
            Objc.sendVoid(arr, Objc.sel("addObject:"),
                NSString.from(TOOLBAR_FLEXIBLE_SPACE_ID));
            for (int i = 0; i < endN; i++) {
                Objc.sendVoid(arr, Objc.sel("addObject:"),
                    NSString.from(TOOLBAR_END_PREFIX + i));
            }
            return arr;
        } catch (Throwable t) {
            t.printStackTrace();
            return Objc.sendPtr(Objc.cls("NSArray"), Objc.sel("array"));
        }
    }

    @SuppressWarnings("unused")
    private static MemorySegment toolbarItemForIdentifier(
            MemorySegment self, MemorySegment cmd,
            MemorySegment toolbar, MemorySegment identifier, boolean willInsert) {
        try {
            String id = NSString.toJava(identifier);
            if (id == null) return MemorySegment.NULL;

            MemorySegment view = null;
            if (id.startsWith(TOOLBAR_START_PREFIX)) {
                int idx = Integer.parseInt(id.substring(TOOLBAR_START_PREFIX.length()));
                java.util.List<MemorySegment> start = TOOLBAR_START_VIEWS.get(self.address());
                if (start != null && idx >= 0 && idx < start.size()) view = start.get(idx);
            } else if (id.startsWith(TOOLBAR_END_PREFIX)) {
                int idx = Integer.parseInt(id.substring(TOOLBAR_END_PREFIX.length()));
                java.util.List<MemorySegment> end = TOOLBAR_END_VIEWS.get(self.address());
                if (end != null && idx >= 0 && idx < end.size()) view = end.get(idx);
            }
            if (view == null) return MemorySegment.NULL;

            // [[NSToolbarItem alloc] initWithItemIdentifier:identifier]
            MemorySegment item = Objc.sendPtr(
                Objc.send_alloc(Objc.cls("NSToolbarItem")),
                Objc.sel("initWithItemIdentifier:"), identifier);
            // Empty label — the view is the item's representation.
            Objc.sendVoid(item, Objc.sel("setLabel:"), NSString.from(""));
            Objc.sendVoid(item, Objc.sel("setView:"), view);
            // NSToolbar will retain this; balance our +1 with autorelease via [item autorelease]
            Objc.sendPtr(item, Objc.sel("autorelease"));
            return item;
        } catch (Throwable t) {
            t.printStackTrace();
            return MemorySegment.NULL;
        }
    }
}
