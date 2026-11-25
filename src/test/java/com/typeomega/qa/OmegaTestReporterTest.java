package com.typeomega.qa;

import net.minecraft.core.BlockPos;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typeomega.qa.report.OmegaTestReporter;
import com.typeomega.qa.spi.DefaultTestReportSink;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.PerfBaselineRecord;
import com.typeomega.qa.spi.TestCaseRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmegaTestReporterTest {

    @Test
    void writesAtomicJunitXmlJsonAndMarkdownWithSkippedAndPerf(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("TEST-omegaqa.xml");
        Path json = tempDir.resolve("omegaqa-result.json");
        Path md = tempDir.resolve("omegaqa-summary.md");
        DefaultTestReportSink sink = new DefaultTestReportSink();

        OmegaTestReporter reporter = new OmegaTestReporter(xml, json, md, false, sink);
        reporter.recordCase(new TestCaseRecord("DemoTests.passCase", "DemoTests", "passCase",
                FailureDiagnostic.TestStatus.PASSED, 120, null, null, null, null, null, null,
                List.of("[t=0001] [STRUCTURE] Formed ok")));
        reporter.recordCase(new TestCaseRecord("DemoTests.failCase", "DemoTests", "failCase",
                FailureDiagnostic.TestStatus.FAILED, 450, 17, "controller_formed", "Missing casing",
                new BlockPos(10, 200, 30), "gtceu:steel_casing", "minecraft:air",
                List.of("[t=0017] [ASSERTION] Missing casing")));
        reporter.recordCase(new TestCaseRecord("DemoTests.skipCase", "DemoTests", "skipCase",
                FailureDiagnostic.TestStatus.SKIPPED, 5, null, null, "gtna not loaded",
                null, null, null, List.of("[t=0000] [LIFECYCLE] SKIPPED: gtna not loaded")));

        sink.recordBenchmark(new PerfBaselineRecord("tokamak_hv", 40, 1.25, 1.10, 1.80, 2.10, 2.40, 0.50, 0,
                List.of(new PerfBaselineRecord.SpikeEntry("typeomega:tokamak", 420.0, 910.0))));

        reporter.finish();

        String xmlText = Files.readString(xml);
        assertTrue(xmlText.contains("tests=\"3\" failures=\"1\" errors=\"0\" skipped=\"1\""));
        assertTrue(xmlText.contains("<failure "));
        assertTrue(xmlText.contains("<skipped message=\"gtna not loaded\"/>"));
        assertTrue(xmlText.contains("<system-out>[t=0017] [ASSERTION] Missing casing</system-out>"));

        JsonObject parsed = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        assertEquals(1, parsed.get("schemaVersion").getAsInt());
        assertEquals("FAILED", parsed.get("status").getAsString());
        assertEquals(1, parsed.get("exitCode").getAsInt());
        assertEquals(3, parsed.getAsJsonObject("counts").get("total").getAsInt());
        assertEquals(1, parsed.getAsJsonArray("benchmarks").size());

        String mdText = Files.readString(md);
        assertTrue(mdText.contains("DemoTests.failCase"));
        assertTrue(mdText.contains("gtna not loaded"));
        assertTrue(mdText.contains("tokamak_hv"));
        assertNotNull(sink.latestSnapshot());
    }

    @Test
    void reportsInfraErrorExitCodeTwoWhenZeroTestsExecuted(@TempDir Path tempDir) throws Exception {
        Path xml = tempDir.resolve("TEST-omegaqa.xml");
        Path json = tempDir.resolve("omegaqa-result.json");
        Path md = tempDir.resolve("omegaqa-summary.md");
        DefaultTestReportSink sink = new DefaultTestReportSink();

        OmegaTestReporter reporter = new OmegaTestReporter(xml, json, md, false, sink);
        reporter.finish();

        JsonObject parsed = JsonParser.parseString(Files.readString(json)).getAsJsonObject();
        assertEquals("INFRA_ERROR", parsed.get("status").getAsString());
        assertEquals(2, parsed.get("exitCode").getAsInt());
    }
}
