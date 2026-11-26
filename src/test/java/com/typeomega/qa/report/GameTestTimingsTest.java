package com.typeomega.qa.report;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typeomega.qa.report.GameTestTimings.TimingEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTestTimingsTest {

    @Test
    void doesNothingWhenPropertyIsUnset() {
        String prev = System.getProperty(OmegaQaProperties.PROP_TIMINGS);
        try {
            System.clearProperty(OmegaQaProperties.PROP_TIMINGS);
            assertNull(OmegaQaProperties.timingsFile());
            assertNull(GameTestTimings.createIfConfigured());
        } finally {
            if (prev != null) System.setProperty(OmegaQaProperties.PROP_TIMINGS, prev);
        }
    }

    @Test
    void buildsTop20SlowestClassAndBatchTotalsAndHeavyList(@TempDir Path tempDir) throws Exception {
        Path jsonPath = tempDir.resolve("timings.json");
        Path txtPath = GameTestTimings.summaryPathFor(jsonPath);
        assertEquals(tempDir.resolve("timings.txt"), txtPath);

        GameTestTimings timings = new GameTestTimings(jsonPath);
        List<TimingEntry> samples = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            String className = (i % 2 == 0) ? "FastTests" : "SlowTests";
            String batch = (i == 25) ? GameTestTimings.HEAVY_BATCH : "defaultBatch";
            boolean heavyBatch = GameTestTimings.HEAVY_BATCH.equals(batch);
            boolean heavyAnnotated = (i == 24 || i == 25);
            timings.record(new TimingEntry(
                    className + ".case" + i,
                    className,
                    "case" + i,
                    batch,
                    heavyBatch,
                    heavyAnnotated,
                    i * 10L,
                    i * 50L,
                    "PASSED"));
            samples.add(timings.entries().get(timings.entries().size() - 1));
        }
        assertEquals(25, samples.size());

        timings.write();

        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(txtPath));

        JsonObject root = JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject();
        assertEquals(GameTestTimings.SCHEMA_VERSION, root.get("schemaVersion").getAsInt());
        assertEquals(25, root.get("totalTests").getAsInt());
        assertEquals(2, root.get("heavyCount").getAsInt());

        JsonArray topSlowest = root.getAsJsonArray("topSlowest");
        assertEquals(20, topSlowest.size());
        assertEquals("SlowTests.case25", topSlowest.get(0).getAsJsonObject().get("testId").getAsString());
        assertEquals(1250L, topSlowest.get(0).getAsJsonObject().get("durationMs").getAsLong());
        assertEquals(250L, topSlowest.get(0).getAsJsonObject().get("ticks").getAsLong());
        assertTrue(topSlowest.get(0).getAsJsonObject().get("heavy").getAsBoolean());
        assertEquals("batch+@HeavyTest", topSlowest.get(0).getAsJsonObject().get("heavyReason").getAsString());

        JsonArray byClass = root.getAsJsonArray("byClass");
        assertEquals(2, byClass.size());
        assertEquals("SlowTests", byClass.get(0).getAsJsonObject().get("className").getAsString());

        JsonArray byBatch = root.getAsJsonArray("byBatch");
        assertEquals(2, byBatch.size());

        JsonArray heavyTests = root.getAsJsonArray("heavyTests");
        assertEquals(2, heavyTests.size());
        assertEquals("SlowTests.case25", heavyTests.get(0).getAsJsonObject().get("testId").getAsString());
        assertEquals("FastTests.case24", heavyTests.get(1).getAsJsonObject().get("testId").getAsString());

        String summary = Files.readString(txtPath);
        assertTrue(summary.contains("Top 20 Slowest Tests"));
        assertTrue(summary.contains("Total By Class"));
        assertTrue(summary.contains("Total By Batch"));
        assertTrue(summary.contains("Heavy Tests (TestBatches.HEAVY / @HeavyTest)"));
        assertTrue(summary.contains("SlowTests.case25"));
        assertTrue(summary.contains("FastTests.case24"));
        assertFalse(summary.contains("SlowTests.case1 "));
    }
}
