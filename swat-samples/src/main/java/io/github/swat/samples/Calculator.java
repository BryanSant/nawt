package io.github.swat.samples;

import io.github.swat.Button;
import io.github.swat.Column;
import io.github.swat.Grid;
import io.github.swat.Label;
import io.github.swat.Toolkit;
import io.github.swat.Widget;
import io.github.swat.Window;
import io.github.swat.spi.Alignment;
import io.github.swat.spi.ChildLayoutConfig;

/**
 * Four-function calculator demonstrating the Grid layout.
 *
 * <p>Layout: a 4-column, 6-row Grid where the display label spans all four
 * columns of row 0, the standard 4×4 number/operator keypad fills rows 1–4,
 * and row 5 hosts {@code 0} (spanning two columns), {@code .}, and {@code =}.
 *
 * <p>State is mutated synchronously on the UI thread via {@code onClickSync}
 * — handlers are trivially fast and serialization there avoids any chance of
 * racing display updates.
 */
public final class Calculator {

    private Calculator() {}

    public static void main(String[] args) {
        Toolkit.launch(Calculator::buildUi);
    }

    private static void buildUi() {
        State state = new State();
        Label display = Label.of(state.display()).fontSize(48).monospace();

        Grid keypad = Grid.builder()
            .spacing(6)
            .homogeneous(true)
            .square()
            // Row 0: display spans all 4 columns, text right-aligned within cell.
            .put(display, 0, 0, 4, 1, ChildLayoutConfig.aligned(Alignment.END))

            // Row 1: clear, sign, percent, divide
            .put(control("AC",  state, State::clear,    display), 0, 1)
            .put(control("±", state, State::negate,  display), 1, 1)
            .put(control("%",   state, State::percent,  display), 2, 1)
            .put(op("÷", "/", state, display),                 3, 1)

            // Row 2: 7 8 9 ×
            .put(digit("7", state, display), 0, 2)
            .put(digit("8", state, display), 1, 2)
            .put(digit("9", state, display), 2, 2)
            .put(op("×", "*", state, display), 3, 2)

            // Row 3: 4 5 6 −
            .put(digit("4", state, display), 0, 3)
            .put(digit("5", state, display), 1, 3)
            .put(digit("6", state, display), 2, 3)
            .put(op("−", "-", state, display), 3, 3)

            // Row 4: 1 2 3 +
            .put(digit("1", state, display), 0, 4)
            .put(digit("2", state, display), 1, 4)
            .put(digit("3", state, display), 2, 4)
            .put(op("+", "+", state, display), 3, 4)

            // Row 5: 0 (2-wide), decimal, equals
            .put(digit("0", state, display), 0, 5, 2, 1)
            .put(control(".", state, State::decimal, display), 2, 5)
            .put(control("=", state, State::equalsOp, display), 3, 5)
            .build();

        // alignCross=CENTER lets the Grid sit at its intrinsic width
        // (= homogeneous column widths summed) instead of being stretched to
        // fill the window, so the buttons size to their content.
        Column content = Column.builder()
            .padding(12).spacing(0)
            .alignCross(Alignment.CENTER)
            .add(keypad)
            .build();

        Window.builder()
            .title("Calculator")
            .size(360, 480)
            .content(content)
            .build()
            .show();
    }

    /** Uniform font size for every key in the keypad. */
    private static final int KEY_FONT_SIZE = 26;

    private static Widget digit(String d, State state, Label display) {
        return Button.of(d).fontSize(KEY_FONT_SIZE).onClickSync(e -> {
            state.digit(d);
            display.text(state.display());
        });
    }

    private static Widget op(String label, String op, State state, Label display) {
        return Button.of(label).fontSize(KEY_FONT_SIZE).onClickSync(e -> {
            state.operator(op);
            display.text(state.display());
        });
    }

    private static Widget control(String label, State state,
                                  java.util.function.Consumer<State> action, Label display) {
        return Button.of(label).fontSize(KEY_FONT_SIZE).onClickSync(e -> {
            action.accept(state);
            display.text(state.display());
        });
    }

    /** Minimal calculator state machine. All mutation runs on the UI thread. */
    private static final class State {
        private String display = "0";
        private Double accumulator;
        private String pendingOp;
        private boolean clearOnNextDigit;
        private boolean errored;

        String display() { return display; }

        void clear() {
            display = "0";
            accumulator = null;
            pendingOp = null;
            clearOnNextDigit = false;
            errored = false;
        }

        void digit(String d) {
            if (errored) clear();
            if (clearOnNextDigit || "0".equals(display)) {
                display = d;
                clearOnNextDigit = false;
            } else {
                display = display + d;
            }
        }

        void decimal() {
            if (errored) clear();
            if (clearOnNextDigit) {
                display = "0.";
                clearOnNextDigit = false;
            } else if (!display.contains(".")) {
                display = display + ".";
            }
        }

        void negate() {
            if (errored || "0".equals(display)) return;
            display = display.startsWith("-") ? display.substring(1) : "-" + display;
        }

        void percent() {
            if (errored) return;
            try { display = format(Double.parseDouble(display) / 100.0); }
            catch (NumberFormatException e) { error(); }
        }

        void operator(String op) {
            if (errored) return;
            applyPending();
            pendingOp = op;
            clearOnNextDigit = true;
        }

        void equalsOp() {
            if (errored) return;
            applyPending();
            pendingOp = null;
        }

        private void applyPending() {
            try {
                double current = Double.parseDouble(display);
                if (pendingOp == null || accumulator == null) {
                    accumulator = current;
                } else {
                    accumulator = switch (pendingOp) {
                        case "+" -> accumulator + current;
                        case "-" -> accumulator - current;
                        case "*" -> accumulator * current;
                        case "/" -> current == 0.0 ? Double.NaN : accumulator / current;
                        default  -> current;
                    };
                }
                display = format(accumulator);
                if ("Error".equals(display)) { errored = true; accumulator = null; pendingOp = null; }
            } catch (NumberFormatException e) {
                error();
            }
        }

        private void error() {
            display = "Error";
            errored = true;
            accumulator = null;
            pendingOp = null;
        }

        private static String format(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) return "Error";
            if (v == Math.floor(v) && Math.abs(v) < 1e15) {
                return Long.toString((long) v);
            }
            return Double.toString(v);
        }
    }
}
