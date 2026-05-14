package cc.nawt.event;

import cc.nawt.TextField;

public record TextChangeEvent(TextField source, String oldText, String newText) {}
