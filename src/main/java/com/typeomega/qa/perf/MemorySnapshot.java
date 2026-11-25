package com.typeomega.qa.perf;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/** Captures JVM heap usage and GC pause totals to measure benchmark memory pressure deltas. */
public record MemorySnapshot(long usedHeapBytes, long gcTimeMs) {

    public record Delta(double heapDeltaMb, long gcPauseMsDelta) {}

    public static MemorySnapshot capture() {
        Runtime rt = Runtime.getRuntime();
        long used = Math.max(0L, rt.totalMemory() - rt.freeMemory());
        long gcMs = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long t = bean.getCollectionTime();
            if (t > 0) gcMs += t;
        }
        return new MemorySnapshot(used, gcMs);
    }

    public Delta deltaSince(MemorySnapshot before) {
        double heapMb = (this.usedHeapBytes - before.usedHeapBytes) / (1024.0 * 1024.0);
        long gcDelta = Math.max(0L, this.gcTimeMs - before.gcTimeMs);
        return new Delta(heapMb, gcDelta);
    }
}
