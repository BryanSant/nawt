package io.github.swat.samples;

import io.github.swat.Button;
import io.github.swat.Canvas;
import io.github.swat.Checkbox;
import io.github.swat.Column;
import io.github.swat.DropDown;
import io.github.swat.Expander;
import io.github.swat.Frame;
import io.github.swat.HeaderBar;
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
import io.github.swat.Toolkit;
import io.github.swat.Tree;
import io.github.swat.Ui;
import io.github.swat.Window;

/** Demonstrates every Tier 1 widget on a single window. */
public final class Tier1Demo {

    private Tier1Demo() {}

    public static void main(String[] args) {
        Toolkit.launch(Tier1Demo::buildUi);
    }

    private static void buildUi() {
        Label status = Label.of("Ready.");

        // Toggle controls
        Checkbox cb = Checkbox.of("Subscribe", true)
            .onToggle(e -> Ui.invokeLater(() -> status.text("Subscribe = " + e.checked())))
            .tooltip("Toggle to subscribe");
        Switch sw = Switch.of(false)
            .onToggle(e -> Ui.invokeLater(() -> status.text("Switch = " + e.checked())));
        Radio r1 = Radio.of("Small", true);
        Radio r2 = Radio.of("Medium");
        Radio r3 = Radio.of("Large");
        r1.group(r2, r3);
        r2.onToggle(e -> { if (e.checked()) Ui.invokeLater(() -> status.text("Size = Medium")); });
        r3.onToggle(e -> { if (e.checked()) Ui.invokeLater(() -> status.text("Size = Large")); });

        Frame togglesFrame = Frame.of("Toggles", Column.builder()
            .spacing(8).padding(12)
            .add(cb).add(sw).add(Row.of(r1, r2, r3))
            .build());

        // Numeric controls
        Slider slider = Slider.of(0, 100, 25);
        ProgressBar progress = ProgressBar.of(0.25);
        Spinner spinner = Spinner.of(true);
        slider.onValueChange(e -> Ui.invokeLater(() -> progress.value(e.value() / 100.0)));

        Frame numericFrame = Frame.of("Numeric", Column.builder()
            .spacing(8).padding(12)
            .add(Label.of("Slider 0–100:"))
            .add(slider)
            .add(progress)
            .add(Row.of(Label.of("Loading…"), spinner))
            .build());

        // DropDown
        DropDown choice = DropDown.of("Apple", "Banana", "Cherry")
            .onSelectionChange(e -> Ui.invokeLater(() ->
                status.text("Picked: " + e.value())));

        // Tree
        Tree tree = Tree.of(new Tree.Node("Animals",
            new Tree.Node("Mammals",
                new Tree.Node("Cat"),
                new Tree.Node("Dog")),
            new Tree.Node("Birds",
                new Tree.Node("Sparrow"),
                new Tree.Node("Eagle"))))
            .onSelectionChange(e -> Ui.invokeLater(() ->
                status.text("Tree: " + (e.label() == null ? "(none)" : e.label()))));

        // Canvas
        Canvas canvas = Canvas.of(160, 80).onPaint(p -> {
            p.color(0.95, 0.95, 0.95).fillRect(0, 0, 160, 80);
            p.color(0.2, 0.5, 0.9).fillRect(10, 10, 60, 60);
            p.color(0.9, 0.3, 0.2).strokeRect(80, 10, 70, 60);
            p.color(0.2, 0.7, 0.3).line(85, 15, 145, 65);
        });

        // Tabs holding a couple sub-pages
        Tabs tabs = Tabs.builder()
            .tab("Dropdown", Column.builder().padding(12).add(choice).build())
            .tab("Tree",     Column.builder().padding(12).add(tree).build())
            .tab("Canvas",   Column.builder().padding(12).add(canvas).build())
            .build();

        // Splitter: numeric on left, tabs on right
        Splitter split = Splitter.horizontal(numericFrame, tabs);

        // Expander wrapping the toggles frame
        Expander expander = Expander.of("Toggles & sizes", togglesFrame).expanded(true);

        // Open URL + clipboard + notification triggers
        Button openSite = Button.of("Open swat.dev")
            .onClick(e -> Toolkit.openUrl("https://example.com"));
        Button copyClip = Button.of("Copy status to clipboard")
            .onClick(e -> {
                Toolkit.setClipboardText(status.text());
                Toolkit.notify("Clipboard", "Copied: " + status.text());
            });
        Row services = Row.of(openSite, copyClip);

        // Drag-and-drop: drag the source label's text onto the target label.
        Label dndSource = Label.of("Drag me →");
        dndSource.dragText(() -> "payload from drag-source");
        Label dndTarget = Label.of("(drop text here)");
        dndTarget.acceptText(t -> Ui.invokeLater(() ->
            dndTarget.text("dropped: " + t)));
        Row dnd = Row.of(dndSource, dndTarget);
        Frame dndFrame = Frame.of("Drag and drop", Column.builder()
            .spacing(8).padding(12).add(dnd).build());

        Column content = Column.builder()
            .spacing(12).padding(16)
            .add(status)
            .add(expander)
            .add(split)
            .add(dndFrame)
            .add(services)
            .build();

        HeaderBar header = HeaderBar.builder()
            .start(Button.of("←")
                .onClick(e -> Ui.invokeLater(() -> status.text("Back"))))
            .start(Button.of("→")
                .onClick(e -> Ui.invokeLater(() -> status.text("Forward"))))
            .end(Button.of("⚙")
                .onClick(e -> Ui.invokeLater(() -> status.text("Settings"))))
            .build();

        Window window = Window.builder()
            .title("SWAT Tier 1")
            .size(820, 560)
            .headerBar(header)
            .content(ScrollContainer.of(content))
            .build();
        window.show();

        // Greeting toast — also confirms toast() works end-to-end.
        Ui.invokeLater(() -> window.toast("Welcome to SWAT Tier 1", 4000));
    }
}
