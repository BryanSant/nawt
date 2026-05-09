package io.github.swat.event;

import io.github.swat.TextField;

public record TextChangeEvent(TextField source, String oldText, String newText) {}
