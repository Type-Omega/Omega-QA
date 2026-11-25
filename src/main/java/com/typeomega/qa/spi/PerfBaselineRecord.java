package com.typeomega.qa.spi;

import java.util.List;

/** Continuous GameTest performance baseline record emitted by {@code PerfBenchRig}. */
public record PerfBaselineRecord(
                                 String benchmarkId,
                                 int ticks,
                                 double avgMs,
                                 double p50Ms,
                                 double p95Ms,
                                 double p99Ms,
                                 double maxMs,
                                 double heapDeltaMb,
                                 long gcPauseMsDelta,
                                 List<SpikeEntry> topSpikes) {

    public PerfBaselineRecord {
        topSpikes = topSpikes == null ? List.of() : List.copyOf(topSpikes);
    }

    public record SpikeEntry(String blockEntityId, double avgMicrosPerTick, double maxMicros) {}
}
