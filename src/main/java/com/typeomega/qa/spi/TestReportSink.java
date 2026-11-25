package com.typeomega.qa.spi;

import org.jetbrains.annotations.Nullable;

/** Bus connecting {@code OmegaTestReporter} / {@code PerfBenchRig} with in-game {@code /omegaqa report}. */
public interface TestReportSink {

    void recordResult(TestCaseRecord record);

    void recordBenchmark(PerfBaselineRecord sample);

    @Nullable
    OmegaReportSnapshot latestSnapshot();

    default void publishSnapshot(OmegaReportSnapshot snapshot) {}
}
