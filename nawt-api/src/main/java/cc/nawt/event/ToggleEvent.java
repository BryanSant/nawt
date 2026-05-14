package cc.nawt.event;

import cc.nawt.Widget;

/** Fired when a boolean-state widget (Checkbox, Switch, Radio) changes value. */
public record ToggleEvent(Widget source, boolean checked) {}
