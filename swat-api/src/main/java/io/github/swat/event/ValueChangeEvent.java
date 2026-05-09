package io.github.swat.event;

import io.github.swat.Widget;

/** Fired when a numeric-valued widget (Slider) changes value. */
public record ValueChangeEvent(Widget source, double value) {}
