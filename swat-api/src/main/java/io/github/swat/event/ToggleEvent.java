package io.github.swat.event;

import io.github.swat.Widget;

/** Fired when a boolean-state widget (Checkbox, Switch, Radio) changes value. */
public record ToggleEvent(Widget source, boolean checked) {}
