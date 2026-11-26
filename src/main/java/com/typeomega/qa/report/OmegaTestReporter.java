package com.typeomega.qa.report;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestTimeoutException;
import net.minecraft.gametest.framework.GlobalTestReporter;
import net.minecraft.gametest.framework.LogTestReporter;
import net.minecraft.gametest.framework.TestReporter;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.OmegaAssumptionException;
import com.typeomega.qa.dsl.OmegaTestSession;
import com.typeomega.qa.spi.DefaultTestReportSink;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.OmegaReportSnapshot;
import com.typeomega.qa.spi.PerfBaselineRecord;
import com.typeomega.qa.spi.TestCaseRecord;
import com.typeomega.qa.spi.TestReportSink;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Unified {@link TestReporter} replacing MC's basic {@code JUnitLikeTestReporter} and publishing
 * {@code TEST-omegaqa.xml}, {@code omegaqa-result.json}, and {@code omegaqa-summary.md} atomically.
 */
public final class OmegaTestReporter implements TestReporter {

    private final Path junitFile;
    private final Path jsonFile;
    private final Path summaryFile;
    private final boolean allowNoTests;
    private final TestReportSink sink;
    private final LogTestReporter logDelegate = new LogTestReporter();
    private final List<TestCaseRecord> recordedTests = new ArrayList<>();
    private final @Nullable GameTestTimings timings;

    public OmegaTestReporter(Path junitFile, Path jsonFile, Path summaryFile, boolean allowNoTests,
                             TestReportSink sink) {
        this.junitFile = Objects.requireNonNull(junitFile, "junitFile");
        this.jsonFile = Objects.requireNonNull(jsonFile, "jsonFile");
        this.summaryFile = Objects.requireNonNull(summaryFile, "summaryFile");
        this.allowNoTests = allowNoTests;
        this.sink = Objects.requireNonNull(sink, "sink");
        this.timings = GameTestTimings.createIfConfigured();
    }

    public static void installIfConfigured() {
        if (!OmegaQaProperties.shouldInstallReporter()) {
            GameTestTimings timings = GameTestTimings.createIfConfigured();
            if (timings != null) {
                GlobalTestReporter.replaceWith(new TimingsOnlyReporter(timings));
                OmegaQA.LOGGER.info("Omega-QA timings reporter installed: {}", OmegaQaProperties.timingsFile());
            }
            return;
        }
        OmegaTestReporter reporter = new OmegaTestReporter(
                OmegaQaProperties.junitFile(),
                OmegaQaProperties.jsonFile(),
                OmegaQaProperties.summaryFile(),
                OmegaQaProperties.allowNoTests(),
                OmegaQaBootstrap.reportSink());
        GlobalTestReporter.replaceWith(reporter);
        OmegaQA.LOGGER.info("Omega-QA reporter installed: xml={}, json={}, md={}",
                reporter.junitFile, reporter.jsonFile, reporter.summaryFile);
    }

    @Override
    public void onTestSuccess(GameTestInfo test) {
        logDelegate.onTestSuccess(test);
        OmegaTestSession session = OmegaTestSession.find(test);
        List<String> eventLines = session == null ? List.of() : session.events().formattedLines();
        if (session != null) session.runCleanup();

        TestCaseRecord record;
        if (session != null && session.isSkipped()) {
            record = toRecord(test, FailureDiagnostic.TestStatus.SKIPPED, null, null, session.skipReason(),
                    null, null, null, eventLines);
        } else {
            record = toRecord(test, FailureDiagnostic.TestStatus.PASSED, null, null, null,
                    null, null, null, eventLines);
            OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                    test.getTestName(), test.getStructureBlockPos(), test.getStructureSize(),
                    null, null, null, "passed", FailureDiagnostic.TestStatus.PASSED));
        }
        recordCase(record);
        if (timings != null) timings.record(test, record.status().name());
        OmegaTestSession.forget(test);
    }

    @Override
    public void onTestFailed(GameTestInfo test) {
        OmegaTestSession session = OmegaTestSession.find(test);
        Throwable error = test.getError();
        List<String> eventLines = session == null ? List.of() : session.events().formattedLines();

        if (error instanceof OmegaAssumptionException || (session != null && session.isSkipped())) {
            String reason = error != null ? error.getMessage() :
                    (session != null ? session.skipReason() : "assumption failed");
            OmegaQA.LOGGER.info("GameTest {} SKIPPED: {}", test.getTestName(), reason);
            OmegaTestSession.clearAssumptionError(test);
            if (session != null) session.runCleanup();
            recordCase(toRecord(test, FailureDiagnostic.TestStatus.SKIPPED, null, null, reason,
                    null, null, null, eventLines));
            if (timings != null) timings.record(test, FailureDiagnostic.TestStatus.SKIPPED.name());
            OmegaTestSession.forget(test);
            return;
        }

        logDelegate.onTestFailed(test);
        if (session != null) session.runCleanup();

        FailureDiagnostic.TestStatus status = classifyError(error);
        Integer failTick = session != null ? session.failTick() : null;
        String invariant = session != null ? session.failInvariant() : null;
        String message = error == null ? "failed" :
                (error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());

        FailureDiagnostic diag = OmegaQaBootstrap.overlaySink().latest(test.getTestName()).orElse(null);
        BlockPos failPos = diag != null ? diag.failPos() :
                (error instanceof GameTestAssertPosException posEx ? posEx.getAbsolutePos() : null);
        String expected = diag != null && diag.expectedState() != null ? diag.expectedState().toString() : null;
        String actual = diag != null && diag.actualState() != null ? diag.actualState().toString() : null;

        if (diag == null) {
            OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                    test.getTestName(), test.getStructureBlockPos(), test.getStructureSize(),
                    failPos, null, null, message, status));
        }
        recordCase(toRecord(test, status, failTick, invariant, message, failPos, expected, actual, eventLines));
        if (timings != null) timings.record(test, status.name());
        OmegaTestSession.forget(test);
    }

    public synchronized void recordCase(TestCaseRecord record) {
        recordedTests.removeIf(r -> r.testId().equals(record.testId()));
        recordedTests.add(record);
        sink.recordResult(record);
    }

    @Override
    public void finish() {
        logDelegate.finish();
        OmegaReportSnapshot snapshot = buildSnapshot();
        sink.publishSnapshot(snapshot);
        try {
            AtomicReportWriter.writeAtomic(junitFile, OmegaTestReportFormatter.toJunitXml(snapshot));
            AtomicReportWriter.writeAtomic(jsonFile, OmegaTestReportFormatter.toJson(snapshot));
            AtomicReportWriter.writeAtomic(summaryFile, OmegaTestReportFormatter.toMarkdown(snapshot));
            OmegaQA.LOGGER.info("Omega-QA wrote reports: {} | {} | {}", junitFile, jsonFile, summaryFile);
        } catch (IOException e) {
            OmegaQA.LOGGER.error("Failed to write Omega-QA reports", e);
        }
        if (timings != null) {
            timings.write();
        }
    }

    private static final class TimingsOnlyReporter implements TestReporter {

        private final LogTestReporter logDelegate = new LogTestReporter();
        private final GameTestTimings timings;

        private TimingsOnlyReporter(GameTestTimings timings) {
            this.timings = timings;
        }

        @Override
        public void onTestSuccess(GameTestInfo test) {
            logDelegate.onTestSuccess(test);
            OmegaTestSession session = OmegaTestSession.find(test);
            String status = (session != null && session.isSkipped()) ? "SKIPPED" : "PASSED";
            timings.record(test, status);
        }

        @Override
        public void onTestFailed(GameTestInfo test) {
            logDelegate.onTestFailed(test);
            Throwable error = test.getError();
            String status = error instanceof OmegaAssumptionException ? "SKIPPED" : classifyError(error).name();
            timings.record(test, status);
        }

        @Override
        public void finish() {
            logDelegate.finish();
            timings.write();
        }
    }

    public synchronized OmegaReportSnapshot buildSnapshot() {
        int passed = 0, failed = 0, errors = 0, skipped = 0;
        for (TestCaseRecord r : recordedTests) {
            switch (r.status()) {
                case PASSED -> passed++;
                case FAILED, TIMED_OUT -> failed++;
                case ERROR -> errors++;
                case SKIPPED -> skipped++;
                case RUNNING -> {}
            }
        }
        int total = passed + failed + errors + skipped;
        List<String> issues = new ArrayList<>();
        String status;
        int exitCode;
        if (total == 0 && !allowNoTests) {
            status = "INFRA_ERROR";
            exitCode = 2;
            issues.add("No GameTests executed and omegaqa.allowNoTests=false");
        } else if (failed + errors > 0) {
            status = "FAILED";
            exitCode = 1;
        } else {
            status = "PASSED";
            exitCode = 0;
        }

        List<PerfBaselineRecord> benches = List.of();
        if (sink instanceof DefaultTestReportSink def) {
            benches = def.benchmarks();
        } else if (sink.latestSnapshot() != null) {
            benches = sink.latestSnapshot().benchmarks();
        }
        return new OmegaReportSnapshot(System.currentTimeMillis(), status, exitCode, total, passed, failed, errors,
                skipped, List.copyOf(recordedTests), benches, List.copyOf(issues));
    }

    private static FailureDiagnostic.TestStatus classifyError(@Nullable Throwable error) {
        if (error instanceof GameTestTimeoutException) return FailureDiagnostic.TestStatus.TIMED_OUT;
        if (error instanceof AssertionError || error instanceof GameTestAssertException) {
            return FailureDiagnostic.TestStatus.FAILED;
        }
        return FailureDiagnostic.TestStatus.ERROR;
    }

    private static TestCaseRecord toRecord(GameTestInfo test, FailureDiagnostic.TestStatus status,
                                           @Nullable Integer failTick, @Nullable String invariant,
                                           @Nullable String message, @Nullable BlockPos failPos,
                                           @Nullable String expectedBlock, @Nullable String actualBlock,
                                           List<String> eventLines) {
        String full = test.getTestName();
        int dot = full.lastIndexOf('.');
        String className = dot > 0 ? full.substring(0, dot) : full;
        String methodName = dot > 0 && dot + 1 < full.length() ? full.substring(dot + 1) : full;
        return new TestCaseRecord(full, className, methodName, status, test.getRunTime(),
                failTick, invariant, message, failPos, expectedBlock, actualBlock, eventLines);
    }
}
