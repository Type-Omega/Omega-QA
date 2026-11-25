package com.typeomega.qa;

import com.typeomega.qa.perf.MemorySnapshot;
import com.typeomega.qa.perf.TickLatencySampler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TickLatencySamplerTest {

    @Test
    void discardsWarmupAndComputesExactPercentiles() {
        TickLatencySampler sampler = new TickLatencySampler(5, 100);
        for (int w = 0; w < 5; w++) {
            assertFalse(sampler.recordNanos(999_000_000L));
        }
        for (int ms = 1; ms <= 100; ms++) {
            boolean done = sampler.recordNanos(ms * 1_000_000L);
            assertEquals(ms == 100, done);
        }
        TickLatencySampler.Stats stats = sampler.compute();
        assertEquals(100, stats.samples());
        assertEquals(50.5, stats.avgMs(), 1e-6);
        assertEquals(50.0, stats.p50Ms(), 1e-6);
        assertEquals(95.0, stats.p95Ms(), 1e-6);
        assertEquals(99.0, stats.p99Ms(), 1e-6);
        assertEquals(100.0, stats.maxMs(), 1e-6);
    }

    @Test
    void capturesMemoryDeltaSafely() {
        MemorySnapshot before = MemorySnapshot.capture();
        MemorySnapshot after = MemorySnapshot.capture();
        MemorySnapshot.Delta delta = after.deltaSince(before);
        assertTrue(before.usedHeapBytes() > 0);
        assertTrue(delta.gcPauseMsDelta() >= 0);
    }
}
