package com.typeomega.qa.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaReportSnapshot;
import com.typeomega.qa.spi.PerfBaselineRecord;
import com.typeomega.qa.spi.TestCaseRecord;

import java.util.Locale;

/** Serializes {@link OmegaReportSnapshot} into JUnit XML, structured JSON v1, and GitHub Step Summary Markdown. */
public final class OmegaTestReportFormatter {

    public static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private OmegaTestReportFormatter() {}

    public static String toJunitXml(OmegaReportSnapshot snapshot) {
        long totalMs = 0L;
        for (TestCaseRecord r : snapshot.tests()) totalMs += Math.max(0L, r.durationMs());
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<testsuite name=\"omegaqa\" tests=\"").append(snapshot.total())
                .append("\" failures=\"").append(snapshot.failed())
                .append("\" errors=\"").append(snapshot.errors())
                .append("\" skipped=\"").append(snapshot.skipped())
                .append("\" time=\"").append(String.format(Locale.ROOT, "%.3f", totalMs / 1000.0)).append("\">\n");

        for (TestCaseRecord t : snapshot.tests()) {
            sb.append("  <testcase classname=\"").append(xmlAttr(t.className()))
                    .append("\" name=\"").append(xmlAttr(t.methodName()))
                    .append("\" time=\"").append(String.format(Locale.ROOT, "%.3f", Math.max(0L, t.durationMs()) /
                            1000.0))
                    .append("\">\n");
            if (t.status() == FailureDiagnostic.TestStatus.FAILED ||
                    t.status() == FailureDiagnostic.TestStatus.TIMED_OUT) {
                String msg = buildFailureSummary(t);
                sb.append("    <failure message=\"").append(xmlAttr(msg))
                        .append("\" type=\"").append(t.status().name()).append("\">")
                        .append(xmlText(msg)).append("</failure>\n");
            } else if (t.status() == FailureDiagnostic.TestStatus.ERROR) {
                String msg = buildFailureSummary(t);
                sb.append("    <error message=\"").append(xmlAttr(msg))
                        .append("\" type=\"ERROR\">").append(xmlText(msg)).append("</error>\n");
            } else if (t.status() == FailureDiagnostic.TestStatus.SKIPPED) {
                String msg = t.message() == null ? "skipped" : t.message();
                sb.append("    <skipped message=\"").append(xmlAttr(msg)).append("\"/>\n");
            }
            if (!t.eventLogLines().isEmpty()) {
                sb.append("    <system-out>").append(xmlText(String.join("\n", t.eventLogLines())))
                        .append("</system-out>\n");
            }
            sb.append("  </testcase>\n");
        }
        sb.append("</testsuite>\n");
        return sb.toString();
    }

    public static String toJson(OmegaReportSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("generatedEpochMs", snapshot.generatedEpochMs());
        root.addProperty("status", snapshot.status());
        root.addProperty("exitCode", snapshot.exitCode());

        JsonObject counts = new JsonObject();
        counts.addProperty("total", snapshot.total());
        counts.addProperty("passed", snapshot.passed());
        counts.addProperty("failed", snapshot.failed());
        counts.addProperty("errors", snapshot.errors());
        counts.addProperty("skipped", snapshot.skipped());
        root.add("counts", counts);

        JsonArray tests = new JsonArray();
        for (TestCaseRecord t : snapshot.tests()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("testId", t.testId());
            obj.addProperty("className", t.className());
            obj.addProperty("methodName", t.methodName());
            obj.addProperty("status", t.status().name());
            obj.addProperty("durationMs", t.durationMs());
            if (t.failTick() != null) obj.addProperty("failTick", t.failTick());
            if (t.invariantName() != null) obj.addProperty("invariant", t.invariantName());
            if (t.message() != null) obj.addProperty("message", t.message());
            if (t.failPos() != null) {
                obj.addProperty("failPos", t.failPos().getX() + "," + t.failPos().getY() + "," + t.failPos().getZ());
            }
            if (t.expectedBlock() != null) obj.addProperty("expectedBlock", t.expectedBlock());
            if (t.actualBlock() != null) obj.addProperty("actualBlock", t.actualBlock());
            JsonArray logArr = new JsonArray();
            for (String line : t.eventLogLines()) logArr.add(line);
            obj.add("eventLog", logArr);
            tests.add(obj);
        }
        root.add("tests", tests);

        JsonArray benches = new JsonArray();
        for (PerfBaselineRecord b : snapshot.benchmarks()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("benchmarkId", b.benchmarkId());
            obj.addProperty("ticks", b.ticks());
            obj.addProperty("avgMs", b.avgMs());
            obj.addProperty("p50Ms", b.p50Ms());
            obj.addProperty("p95Ms", b.p95Ms());
            obj.addProperty("p99Ms", b.p99Ms());
            obj.addProperty("maxMs", b.maxMs());
            obj.addProperty("heapDeltaMb", b.heapDeltaMb());
            obj.addProperty("gcPauseMsDelta", b.gcPauseMsDelta());
            JsonArray spikes = new JsonArray();
            for (PerfBaselineRecord.SpikeEntry spike : b.topSpikes()) {
                JsonObject s = new JsonObject();
                s.addProperty("blockEntityId", spike.blockEntityId());
                s.addProperty("avgMicrosPerTick", spike.avgMicrosPerTick());
                s.addProperty("maxMicros", spike.maxMicros());
                spikes.add(s);
            }
            obj.add("topSpikes", spikes);
            benches.add(obj);
        }
        root.add("benchmarks", benches);

        JsonArray issues = new JsonArray();
        for (String issue : snapshot.issues()) issues.add(issue);
        root.add("issues", issues);
        return GSON.toJson(root) + "\n";
    }

    public static String toMarkdown(OmegaReportSnapshot snapshot) {
        StringBuilder md = new StringBuilder();
        md.append("# Omega-QA Summary — ").append(snapshot.status()).append("\n\n");
        md.append(String.format(Locale.ROOT,
                "**Total:** %d | **Passed:** %d | **Failed:** %d | **Errors:** %d | **Skipped:** %d | **Exit:** %d\n\n",
                snapshot.total(), snapshot.passed(), snapshot.failed(), snapshot.errors(), snapshot.skipped(),
                snapshot.exitCode()));

        if (!snapshot.issues().isEmpty()) {
            md.append("## Infrastructure Issues\n\n");
            for (String issue : snapshot.issues()) md.append("- ").append(issue).append("\n");
            md.append("\n");
        }

        var failures = snapshot.tests().stream()
                .filter(t -> t.status() == FailureDiagnostic.TestStatus.FAILED ||
                        t.status() == FailureDiagnostic.TestStatus.TIMED_OUT ||
                        t.status() == FailureDiagnostic.TestStatus.ERROR)
                .toList();
        if (!failures.isEmpty()) {
            md.append("## Failures & Errors\n\n");
            for (TestCaseRecord f : failures) {
                md.append("- **`").append(f.className()).append(".").append(f.methodName()).append("`** [")
                        .append(f.status()).append("]");
                if (f.failTick() != null) md.append(" at tick `").append(f.failTick()).append("`");
                if (f.invariantName() != null) md.append(" invariant `").append(f.invariantName()).append("`");
                md.append(": ").append(f.message() == null ? "" : f.message()).append("\n");
                if (f.expectedBlock() != null || f.actualBlock() != null) {
                    md.append("  - Expected: `").append(f.expectedBlock()).append("` | Actual: `")
                            .append(f.actualBlock()).append("`");
                    if (f.failPos() != null) md.append(" at `").append(f.failPos().toShortString()).append("`");
                    md.append("\n");
                }
            }
            md.append("\n");
        }

        var skipped = snapshot.tests().stream()
                .filter(t -> t.status() == FailureDiagnostic.TestStatus.SKIPPED)
                .toList();
        if (!skipped.isEmpty()) {
            md.append("## Skipped Tests\n\n");
            md.append("| Test | Reason |\n| --- | --- |\n");
            for (TestCaseRecord s : skipped) {
                md.append("| `").append(s.testId()).append("` | ")
                        .append(s.message() == null ? "" : s.message().replace("|", "\\|")).append(" |\n");
            }
            md.append("\n");
        }

        if (!snapshot.benchmarks().isEmpty()) {
            md.append("## Performance Baselines\n\n");
            md.append("| Benchmark | Ticks | Avg ms/t | p95 | p99 | Max | Heap Δ MB | Top BE |\n");
            md.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |\n");
            for (PerfBaselineRecord b : snapshot.benchmarks()) {
                String topBe = b.topSpikes().isEmpty() ? "-" :
                        String.format(Locale.ROOT, "%s (%.1f us)", b.topSpikes().get(0).blockEntityId(),
                                b.topSpikes().get(0).avgMicrosPerTick());
                md.append(String.format(Locale.ROOT,
                        "| `%s` | %d | %.3f | %.3f | %.3f | %.3f | %.2f | %s |\n",
                        b.benchmarkId(), b.ticks(), b.avgMs(), b.p95Ms(), b.p99Ms(), b.maxMs(),
                        b.heapDeltaMb(), topBe));
            }
            md.append("\n");
        }
        return md.toString();
    }

    private static String buildFailureSummary(TestCaseRecord t) {
        StringBuilder sb = new StringBuilder();
        if (t.failTick() != null) sb.append("[tick=").append(t.failTick()).append("] ");
        if (t.invariantName() != null) sb.append("[invariant=").append(t.invariantName()).append("] ");
        if (t.message() != null) sb.append(t.message());
        if (t.expectedBlock() != null || t.actualBlock() != null) {
            sb.append(" [expected=").append(t.expectedBlock()).append(", actual=").append(t.actualBlock()).append("]");
        }
        return sb.toString();
    }

    private static String xmlAttr(String value) {
        return xmlText(value).replace("\"", "&quot;").replace("'", "&apos;").replace("\n", "&#10;");
    }

    private static String xmlText(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> {
                    if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xD7FF) ||
                            (c >= 0xE000 && c <= 0xFFFD)) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
