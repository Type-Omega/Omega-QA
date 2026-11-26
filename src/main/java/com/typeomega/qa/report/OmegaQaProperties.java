package com.typeomega.qa.report;

import net.minecraftforge.gametest.ForgeGameTestHooks;

import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Resolves {@code -Domegaqa.*} JVM flags.
 */
public final class OmegaQaProperties {

    public enum Mode {
        CI,
        INTERACTIVE,
        OFF
    }

    public static final String PROP_MODE = "omegaqa.mode";
    public static final String PROP_REPORT_DIR = "omegaqa.reportDir";
    public static final String PROP_JUNIT_FILE = "omegaqa.junitFile";
    public static final String PROP_JSON_FILE = "omegaqa.jsonFile";
    public static final String PROP_SUMMARY_FILE = "omegaqa.summaryFile";
    public static final String PROP_TESTS = "omegaqa.tests";
    public static final String PROP_ALLOW_NO_TESTS = "omegaqa.allowNoTests";
    public static final String PROP_EXPORT_SNAPSHOT = "omegaqa.exportSnapshot";
    public static final String PROP_SNAPSHOT_DIR = "omegaqa.snapshotDir";
    public static final String PROP_TURBO = "omegaqa.turbo";
    public static final String PROP_TIMINGS = "omegaqa.timings";

    private OmegaQaProperties() {}

    public static Mode mode() {
        String raw = System.getProperty(PROP_MODE);
        if (raw != null && !raw.isBlank()) {
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "ci" -> Mode.CI;
                case "off" -> Mode.OFF;
                default -> Mode.INTERACTIVE;
            };
        }
        if (hasAnyReportFlag() || ForgeGameTestHooks.isGametestServer()) {
            return Mode.CI;
        }
        return Mode.INTERACTIVE;
    }

    public static boolean shouldInstallReporter() {
        Mode m = mode();
        if (m == Mode.OFF) return false;
        return m == Mode.CI || hasAnyReportFlag();
    }

    public static Path reportDir() {
        String raw = System.getProperty(PROP_REPORT_DIR);
        if (raw != null && !raw.isBlank()) return resolvePath(raw);
        return resolvePath("build/reports/omegaqa");
    }

    public static Path junitFile() {
        String explicit = System.getProperty(PROP_JUNIT_FILE);
        if (explicit != null && !explicit.isBlank()) return resolvePath(explicit);
        return reportDir().resolve("TEST-omegaqa.xml");
    }

    public static Path jsonFile() {
        String explicit = System.getProperty(PROP_JSON_FILE);
        if (explicit != null && !explicit.isBlank()) return resolvePath(explicit);
        return reportDir().resolve("omegaqa-result.json");
    }

    public static Path summaryFile() {
        String explicit = System.getProperty(PROP_SUMMARY_FILE);
        if (explicit != null && !explicit.isBlank()) return resolvePath(explicit);
        return reportDir().resolve("omegaqa-summary.md");
    }

    public static @Nullable Path timingsFile() {
        String explicit = System.getProperty(PROP_TIMINGS);
        if (explicit == null || explicit.isBlank()) return null;
        return resolvePath(explicit);
    }

    public static List<String> testFilters() {
        String raw = System.getProperty(PROP_TESTS);
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public static boolean allowNoTests() {
        return Boolean.parseBoolean(System.getProperty(PROP_ALLOW_NO_TESTS, "false"));
    }

    public static boolean exportSnapshot() {
        return Boolean.parseBoolean(System.getProperty(PROP_EXPORT_SNAPSHOT, "false"));
    }

    public static Path snapshotDir() {
        String raw = System.getProperty(PROP_SNAPSHOT_DIR);
        if (raw != null && !raw.isBlank()) return resolvePath(raw);
        return reportDir().resolve("runtime_snapshot");
    }

    private static Path resolvePath(String raw) {
        Path path = Paths.get(raw.trim());
        if (path.isAbsolute()) return path.normalize();
        // EN: Dev runs execute with CWD=<project>/run; resolve relative report paths against the Gradle project root.
        // PT: Runs de dev executam com CWD=<projeto>/run; caminhos relativos resolvem a partir da raiz do Gradle.
        // ZH: 开发运行以 <project>/run 为工作目录；将相对报告路径解析为相对于 Gradle 项目根目录。
        Path parentGradle = Paths.get("../build.gradle");
        if (java.nio.file.Files.exists(parentGradle)) {
            return Paths.get("..").resolve(path).toAbsolutePath().normalize();
        }
        return path.toAbsolutePath().normalize();
    }

    public static int turboFactor() {
        String raw = System.getProperty(PROP_TURBO, "1");
        try {
            int val = Integer.parseInt(raw.trim());
            return Math.max(1, Math.min(100, val));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static boolean hasAnyReportFlag() {
        return isSet(PROP_REPORT_DIR) || isSet(PROP_JUNIT_FILE) || isSet(PROP_JSON_FILE) ||
                isSet(PROP_SUMMARY_FILE);
    }

    private static boolean isSet(String key) {
        String v = System.getProperty(key);
        return v != null && !v.isBlank();
    }
}
