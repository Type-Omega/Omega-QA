package com.typeomega.qa.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded per-test flight recorder (capped at {@link #MAX_ENTRIES} entries to protect CI memory).
 */
public final class EventLog {

    public static final int MAX_ENTRIES = 10_000;

    private final List<EventEntry> entries = new ArrayList<>();
    private boolean truncated;

    public synchronized void record(long relativeTick, EventCategory category, String message) {
        Objects.requireNonNull(category, "category");
        if (entries.size() >= MAX_ENTRIES) {
            if (!truncated) {
                truncated = true;
                entries.set(MAX_ENTRIES - 1, new EventEntry(relativeTick, EventCategory.LIFECYCLE,
                        "EventLog truncated at " + MAX_ENTRIES + " entries"));
            }
            return;
        }
        entries.add(new EventEntry(relativeTick, category, message));
    }

    public synchronized List<EventEntry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public synchronized List<String> formattedLines() {
        List<String> lines = new ArrayList<>(entries.size());
        for (EventEntry entry : entries) {
            lines.add(entry.format());
        }
        return Collections.unmodifiableList(lines);
    }

    public synchronized boolean contains(EventCategory category, String substring) {
        for (EventEntry entry : entries) {
            if (entry.category() == category && entry.message().contains(substring)) return true;
        }
        return false;
    }

    public synchronized void assertLogged(EventCategory category, String substring) {
        if (!contains(category, substring)) {
            throw new AssertionError("Expected EventLog entry [" + category + "] containing '" + substring + "'");
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized boolean isTruncated() {
        return truncated;
    }

    public synchronized void clear() {
        entries.clear();
        truncated = false;
    }
}
