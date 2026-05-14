package cc.nawt.spi;

public record SliderConfig(double min, double max, double initialValue, Orientation orientation) {
    public SliderConfig {
        if (orientation == null) orientation = Orientation.HORIZONTAL;
        if (max < min) throw new IllegalArgumentException("max < min");
        if (initialValue < min) initialValue = min;
        if (initialValue > max) initialValue = max;
    }
}
