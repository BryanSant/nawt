package cc.nawt.spi;

/** {@code value} is in [0, 1]. {@code indeterminate} ignores {@code value} and shows a busy animation. */
public record ProgressBarConfig(double value, boolean indeterminate) {
    public ProgressBarConfig {
        if (Double.isNaN(value)) value = 0;
        if (value < 0) value = 0;
        if (value > 1) value = 1;
    }
}
