package cc.nawt.event;

import cc.nawt.Widget;

/** Fired when a numeric-valued widget (Slider) changes value. */
public record ValueChangeEvent(Widget source, double value) {}
