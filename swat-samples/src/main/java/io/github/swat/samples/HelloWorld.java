package io.github.swat.samples;

import io.github.swat.Button;
import io.github.swat.Column;
import io.github.swat.Label;
import io.github.swat.TextField;
import io.github.swat.Toolkit;
import io.github.swat.Ui;
import io.github.swat.Window;

public final class HelloWorld {

    private HelloWorld() {}

    public static void main(String[] args) {
        Toolkit.launch(HelloWorld::buildUi);
    }

    private static void buildUi() {
        Label greeting = Label.of("Hello, world.");
        TextField name = TextField.of();
        Button greet = Button.of("Greet")
            .onClick(e -> {
                String who = name.text();
                String message = who.isBlank() ? "Hello, world." : "Hello, " + who + ".";
                Ui.invokeLater(() -> greeting.text(message));
            });

        Column content = Column.builder()
            .spacing(12)
            .padding(16)
            .add(greeting)
            .add(name)
            .add(greet)
            .build();

        Window.builder()
            .title("Hello SWAT")
            .size(400, 300)
            .content(content)
            .build()
            .show();
    }
}
