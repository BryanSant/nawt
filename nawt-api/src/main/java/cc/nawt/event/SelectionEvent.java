package cc.nawt.event;

import cc.nawt.Widget;

/**
 * Fired when a selection-bearing widget (ListView, DropDown) changes.
 * {@code index} is -1 when nothing is selected; {@code value} is null in that case.
 */
public record SelectionEvent(Widget source, int index, String value) {}
