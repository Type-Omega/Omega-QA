package com.typeomega.qa.log;

import java.util.Locale;
import java.util.Objects;

/** Single flight-recorder entry emitted during a GameTest run. */
public record EventEntry(long relativeTick, EventCategory category, String message) {

    public EventEntry {
        Objects.requireNonNull(category, "category");
        message = message == null ? "" : message;
    }

    public String format() {
        return String.format(Locale.ROOT, "[t=%04d] [%s] %s", relativeTick, category.name(), message);
    }
}
