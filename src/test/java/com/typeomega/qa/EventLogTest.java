package com.typeomega.qa;

import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.log.EventLog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogTest {

    @Test
    void formatsEntriesAndEnforcesMaxCapacity() {
        EventLog log = new EventLog();
        log.record(42, EventCategory.STRUCTURE, "Formed typeomega:tokamak_hv");
        assertEquals("[t=0042] [STRUCTURE] Formed typeomega:tokamak_hv", log.formattedLines().get(0));
        log.assertLogged(EventCategory.STRUCTURE, "tokamak_hv");
        assertThrows(AssertionError.class, () -> log.assertLogged(EventCategory.AE2, "missing"));

        for (int i = 0; i < EventLog.MAX_ENTRIES + 500; i++) {
            log.record(i, EventCategory.PERF, "tick " + i);
        }
        assertEquals(EventLog.MAX_ENTRIES, log.size());
        assertTrue(log.isTruncated());
        assertTrue(log.formattedLines().get(EventLog.MAX_ENTRIES - 1).contains("truncated"));
    }
}
