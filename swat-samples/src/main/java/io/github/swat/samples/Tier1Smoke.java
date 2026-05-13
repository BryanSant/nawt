package io.github.swat.samples;

import io.github.swat.Button;
import io.github.swat.Canvas;
import io.github.swat.Checkbox;
import io.github.swat.Column;
import io.github.swat.DropDown;
import io.github.swat.Expander;
import io.github.swat.Frame;
import io.github.swat.Grid;
import io.github.swat.HeaderBar;
import io.github.swat.Image;
import io.github.swat.Label;
import io.github.swat.ProgressBar;
import io.github.swat.Radio;
import io.github.swat.Row;
import io.github.swat.ScrollContainer;
import io.github.swat.Slider;
import io.github.swat.Spinner;
import io.github.swat.Splitter;
import io.github.swat.Switch;
import io.github.swat.Tabs;
import io.github.swat.TextField;
import io.github.swat.Toolkit;
import io.github.swat.Tree;
import io.github.swat.Window;
import io.github.swat.spi.Alignment;
import io.github.swat.spi.ChildLayoutConfig;

/**
 * Constructs every Tier 1 widget, opens a window, exercises a handful of setters,
 * then quits. Prints {@code TIER1_OK} on success.
 */
public final class Tier1Smoke {

    private Tier1Smoke() {}

    public static void main(String[] args) {
        Toolkit.launch(Tier1Smoke::body);
        System.out.println("TIER1_OK");
    }

    private static void body() {
        Label label = Label.of("hi");
        TextField field = TextField.of("ada");
        Button button = Button.of("go");

        Checkbox cb = Checkbox.of("opt", true);
        Switch sw = Switch.of(false);
        Radio r1 = Radio.of("a", true);
        Radio r2 = Radio.of("b");
        r1.group(r2);

        Slider slider = Slider.of(0, 10, 5);
        ProgressBar pb = ProgressBar.of(0.5);
        Spinner spinner = Spinner.of(true);
        DropDown dd = DropDown.of("x", "y", "z");
        dd.select(1);

        Tree tree = Tree.of(new Tree.Node("root",
            new Tree.Node("a"), new Tree.Node("b", new Tree.Node("b.1"))));

        Image image = Image.fromFile(""); // empty path is OK
        Canvas canvas = Canvas.of(40, 40)
            .onPaint(p -> p.color(0.1, 0.2, 0.3, 1.0).fillRect(0, 0, 40, 40));

        Frame frame = Frame.of("frame", Column.of(label, field, button));
        ScrollContainer scroll = ScrollContainer.of(Column.of(cb, sw, r1, r2));
        Tabs tabs = Tabs.builder()
            .tab("one", Column.of(slider, pb))
            .tab("two", Column.of(dd, spinner))
            .tab("three", Column.of(tree))
            .build();
        Splitter split = Splitter.horizontal(scroll, tabs);
        Expander exp = Expander.of("more", Row.of(image, canvas)).expanded(true);

        // Tooltip / dnd API surface
        button.tooltip("does something");
        field.dragText(() -> "dragged");
        label.acceptText(t -> { /* would receive */ });

        // Layout API smoke: Row with an expanding child and a Column with a
        // per-child cross-axis alignment override. Construction-only — no
        // visual assertion, just makes sure the SPI path doesn't throw.
        Row expandingRow = Row.builder()
            .add(Label.of("L"))
            .expand(TextField.of("middle"))
            .add(Button.of("R"))
            .build();
        Column alignedColumn = Column.builder()
            .alignCross(Alignment.START)
            .add(Button.of("start"))
            .add(Button.of("center"), ChildLayoutConfig.aligned(Alignment.CENTER))
            .add(Button.of("end"), ChildLayoutConfig.aligned(Alignment.END))
            .build();

        // Grid smoke: 2 cols × 3 rows with one cell spanning both columns and
        // one cell using alignSelf=CENTER. Construction-only.
        Grid grid = Grid.builder()
            .columnSpacing(8).rowSpacing(6)
            .put(Label.of("Name"), 0, 0)
            .put(TextField.of("ada"), 1, 0, ChildLayoutConfig.EXPAND)
            .put(Label.of("Status"), 0, 1)
            .put(DropDown.of("active", "inactive"), 1, 1,
                ChildLayoutConfig.aligned(Alignment.CENTER))
            .put(Button.of("Save"), 0, 2, 2, 1)
            .build();

        Column content = Column.builder()
            .padding(8).spacing(8)
            .add(frame).add(split).add(exp)
            .add(expandingRow).add(alignedColumn).add(grid)
            .build();

        HeaderBar header = HeaderBar.builder()
            .start(Button.of("←"))
            .end(Button.of("⚙"))
            .build();

        Window w = Window.builder()
            .title("Tier 1 Smoke")
            .size(640, 480)
            .headerBar(header)
            .content(content)
            .build();
        w.show();
        // Exercise the toast SPI path (canary-only, no visual verification).
        w.toast("smoke ok", 1000);

        // Exercise setters
        slider.value(7.5);
        pb.value(0.75);
        cb.checked(false);
        sw.on(true);
        r2.selected(true);
        tree.select(1, 0);

        // Toolkit-level (clipboard set/get round-trip is best-effort)
        Toolkit.setClipboardText("swat-clip");

        try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        w.close();
    }
}
