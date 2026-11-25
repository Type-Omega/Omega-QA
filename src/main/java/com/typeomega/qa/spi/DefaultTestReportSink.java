package com.typeomega.qa.spi;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Default thread-safe in-memory {@link TestReportSink}. */
public final class DefaultTestReportSink implements TestReportSink {

    private final List<TestCaseRecord> results = new CopyOnWriteArrayList<>();
    private final List<PerfBaselineRecord> benchmarks = new CopyOnWriteArrayList<>();
    private final AtomicReference<OmegaReportSnapshot> latest = new AtomicReference<>();

    @Override
    public void recordResult(TestCaseRecord record) {
        if (record == null) return;
        results.removeIf(existing -> existing.testId().equals(record.testId()));
        results.add(record);
        rebuildSnapshot();
    }

    @Override
    public void recordBenchmark(PerfBaselineRecord sample) {
        if (sample == null) return;
        benchmarks.removeIf(existing -> existing.benchmarkId().equals(sample.benchmarkId()));
        benchmarks.add(sample);
        rebuildSnapshot();
    }

    @Override
    public @Nullable OmegaReportSnapshot latestSnapshot() {
        return latest.get();
    }

    @Override
    public void publishSnapshot(OmegaReportSnapshot snapshot) {
        latest.set(snapshot);
    }

    public List<TestCaseRecord> results() {
        return List.copyOf(results);
    }

    public List<PerfBaselineRecord> benchmarks() {
        return List.copyOf(benchmarks);
    }

    public void clear() {
        results.clear();
        benchmarks.clear();
        latest.set(null);
    }

    private void rebuildSnapshot() {
        List<TestCaseRecord> snapshotTests = new ArrayList<>(results);
        int passed = 0, failed = 0, errors = 0, skipped = 0;
        for (TestCaseRecord r : snapshotTests) {
            switch (r.status()) {
                case PASSED -> passed++;
                case FAILED, TIMED_OUT -> failed++;
                case ERROR -> errors++;
                case SKIPPED -> skipped++;
                case RUNNING -> {}
            }
        }
        int total = passed + failed + errors + skipped;
        String status = (failed + errors) > 0 ? "FAILED" : "PASSED";
        int exitCode = (failed + errors) > 0 ? 1 : 0;
        latest.set(new OmegaReportSnapshot(System.currentTimeMillis(), status, exitCode, total, passed, failed, errors,
                skipped, snapshotTests, List.copyOf(benchmarks), List.of()));
    }
}
