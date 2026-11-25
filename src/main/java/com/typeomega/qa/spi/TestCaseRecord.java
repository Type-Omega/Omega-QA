package com.typeomega.qa.spi;

import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Structured test outcome record emitted to {@link TestReportSink} and serialized to JSON/Markdown/JUnit. */
public record TestCaseRecord(
                             String testId,
                             String className,
                             String methodName,
                             FailureDiagnostic.TestStatus status,
                             long durationMs,
                             @Nullable Integer failTick,
                             @Nullable String invariantName,
                             @Nullable String message,
                             @Nullable BlockPos failPos,
                             @Nullable String expectedBlock,
                             @Nullable String actualBlock,
                             List<String> eventLogLines) {

    public TestCaseRecord {
        eventLogLines = eventLogLines == null ? List.of() : List.copyOf(eventLogLines);
    }
}
