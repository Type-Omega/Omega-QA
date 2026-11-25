package com.typeomega.qa.perf;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.PerfBaselineRecord;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Continuous GameTest benchmark harness measuring {@code avg}/{@code p50}/{@code p95}/{@code p99}/{@code max}
 * ms/tick, JVM heap/GC deltas, and per-{@code BlockEntity} tick spikes, emitting {@link PerfBaselineRecord} samples
 * without failing on a rigid numeric ceiling.
 */
public final class PerfBenchRig {

    private final OmegaTestHelper helper;
    private final String benchmarkId;
    private int warmupTicks = 10;
    private int measureTicks = 40;
    private @Nullable BoundingBox monitoredBox;
    private @Nullable Runnable perTickAction;
    private @Nullable Runnable functionalCheck;

    private PerfBenchRig(OmegaTestHelper helper, String benchmarkId) {
        this.helper = Objects.requireNonNull(helper, "helper");
        this.benchmarkId = Objects.requireNonNull(benchmarkId, "benchmarkId");
    }

    public static PerfBenchRig of(GameTestHelper helper, String benchmarkId) {
        return new PerfBenchRig(OmegaTestHelper.wrap(helper), benchmarkId);
    }

    public PerfBenchRig warmupTicks(int warmupTicks) {
        this.warmupTicks = warmupTicks;
        return this;
    }

    public PerfBenchRig measureTicks(int measureTicks) {
        this.measureTicks = measureTicks;
        return this;
    }

    public PerfBenchRig monitorBox(BoundingBox box) {
        this.monitoredBox = box;
        return this;
    }

    public PerfBenchRig onBenchTick(Runnable action) {
        this.perTickAction = action;
        return this;
    }

    /** Optional functional invariant checked every tick; functional failure fails the test, latency never does. */
    public PerfBenchRig requireFunctional(Runnable check) {
        this.functionalCheck = check;
        return this;
    }

    /** Executes across live server ticks and calls {@code succeed()} after {@code warmup + measure} ticks. */
    public void runLive() {
        TickLatencySampler sampler = new TickLatencySampler(warmupTicks, measureTicks);
        BlockEntitySpikeProfiler profiler = new BlockEntitySpikeProfiler();
        MemorySnapshot before = MemorySnapshot.capture();
        final int[] tickCount = { 0 };
        final boolean[] emitted = { false };

        helper.onEachTick("perf:" + benchmarkId, () -> {
            if (emitted[0]) return;
            long t0 = System.nanoTime();
            if (perTickAction != null) perTickAction.run();
            if (monitoredBox != null && tickCount[0] >= warmupTicks) {
                profiler.profileRegionTick(helper.level(), monitoredBox);
            }
            if (functionalCheck != null) functionalCheck.run();
            long dt = System.nanoTime() - t0;
            tickCount[0]++;

            if (sampler.recordNanos(dt)) {
                emitted[0] = true;
                emit(sampler, profiler, before);
                helper.succeed();
            }
        });
    }

    /** Executes synchronously for deterministic benchmark steps and returns the recorded baseline. */
    public PerfBaselineRecord runSynchronous() {
        TickLatencySampler sampler = new TickLatencySampler(warmupTicks, measureTicks);
        BlockEntitySpikeProfiler profiler = new BlockEntitySpikeProfiler();
        MemorySnapshot before = MemorySnapshot.capture();
        int total = warmupTicks + measureTicks;
        for (int i = 0; i < total; i++) {
            long t0 = System.nanoTime();
            if (perTickAction != null) perTickAction.run();
            if (monitoredBox != null && i >= warmupTicks) {
                profiler.profileRegionTick(helper.level(), monitoredBox);
            }
            if (functionalCheck != null) functionalCheck.run();
            sampler.recordNanos(System.nanoTime() - t0);
        }
        return emit(sampler, profiler, before);
    }

    private PerfBaselineRecord emit(TickLatencySampler sampler, BlockEntitySpikeProfiler profiler,
                                    MemorySnapshot before) {
        TickLatencySampler.Stats stats = sampler.compute();
        MemorySnapshot.Delta memDelta = MemorySnapshot.capture().deltaSince(before);
        PerfBaselineRecord record = new PerfBaselineRecord(
                benchmarkId,
                stats.samples(),
                stats.avgMs(),
                stats.p50Ms(),
                stats.p95Ms(),
                stats.p99Ms(),
                stats.maxMs(),
                memDelta.heapDeltaMb(),
                memDelta.gcPauseMsDelta(),
                profiler.topOffenders(5));
        OmegaQaBootstrap.reportSink().recordBenchmark(record);
        helper.log(EventCategory.PERF, String.format(Locale.ROOT,
                "BENCH %s ticks=%d avg=%.3fms p50=%.3fms p95=%.3fms p99=%.3fms max=%.3fms heap=%.2fMB",
                benchmarkId, record.ticks(), record.avgMs(), record.p50Ms(), record.p95Ms(), record.p99Ms(),
                record.maxMs(), record.heapDeltaMb()));
        return record;
    }
}
