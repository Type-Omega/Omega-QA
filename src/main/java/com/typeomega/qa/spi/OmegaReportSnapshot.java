package com.typeomega.qa.spi;

import java.util.List;

/** Immutable report snapshot shared between CI reporters and in-game {@code /omegaqa report}. */
public record OmegaReportSnapshot(
                                  long generatedEpochMs,
                                  String status,
                                  int exitCode,
                                  int total,
                                  int passed,
                                  int failed,
                                  int errors,
                                  int skipped,
                                  List<TestCaseRecord> tests,
                                  List<PerfBaselineRecord> benchmarks,
                                  List<String> issues) {

    public OmegaReportSnapshot {
        tests = tests == null ? List.of() : List.copyOf(tests);
        benchmarks = benchmarks == null ? List.of() : List.copyOf(benchmarks);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
