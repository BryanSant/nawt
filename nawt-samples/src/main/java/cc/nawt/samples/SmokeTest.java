package cc.nawt.samples;

import cc.nawt.Button;
import cc.nawt.Column;
import cc.nawt.Label;
import cc.nawt.ListView;
import cc.nawt.TextField;
import cc.nawt.Toolkit;
import cc.nawt.Ui;
import cc.nawt.Window;
import cc.nawt.menu.Menu;
import cc.nawt.menu.MenuAction;
import cc.nawt.menu.MenuBar;
import cc.nawt.menu.MenuSeparator;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-interactive smoke test. Builds a window with a menu bar, list view,
 * label, text field, and button. Exercises peer round-trips, programmatic
 * selection, item replacement, and menu construction. Prints {@code SMOKE_OK}
 * when everything succeeds and the toolkit shuts down cleanly.
 */
public final class SmokeTest {

    private SmokeTest() {}

    public static void main(String[] args) {
        Toolkit.launch(SmokeTest::body);
        System.out.println("SMOKE_OK");
    }

    private static void body() {
        if (Ui.isUiThread()) fail("app body must run on a virtual thread");

        Label greeting = Label.of("Hello, world.");
        TextField name = TextField.of("Ada");
        ListView fruits = ListView.of("Apple", "Banana", "Cherry");
        AtomicInteger lastSelection = new AtomicInteger(-99);
        fruits.onSelectionChange(e -> lastSelection.set(e.index()));

        Button greet = Button.of("Greet").onClick(e -> { /* noop in smoke */ });

        Column content = Column.builder()
            .spacing(12).padding(16)
            .add(greeting).add(name).add(fruits).add(greet)
            .build();

        MenuBar menuBar = MenuBar.of(
            Menu.builder("File")
                .add(MenuAction.of("New"))
                .add(MenuAction.of("Open"))
                .add(MenuSeparator.of())
                .add(MenuAction.of("Close"))
                .build(),
            Menu.builder("Help")
                .add(MenuAction.of("About"))
                .build()
        );

        Window w = Window.builder()
            .title("Nawt Smoke")
            .size(420, 320)
            .menuBar(menuBar)
            .content(content)
            .build();
        w.show();

        // Exercise text round-trip
        if (!"Ada".equals(name.text())) fail("text round-trip failed: " + name.text());

        // Programmatic list selection + items() change
        fruits.select(1);
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        if (fruits.selectedIndex() != 1) fail("selectedIndex expected 1, got " + fruits.selectedIndex());

        fruits.items(List.of("X", "Y"));
        if (fruits.items().size() != 2) fail("items().size expected 2, got " + fruits.items().size());

        // Hold briefly so a human can see it; then close to quit.
        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
        w.close();
    }

    private static void fail(String msg) {
        System.err.println("FAIL: " + msg);
        Toolkit.shutdown();
        throw new AssertionError(msg);
    }
}
