package com.typeomega.qa.report;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.forgespi.language.ModFileScanData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.typeomega.qa.OmegaQA;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Collects per-test execution timings (ticks and real ms) from {@link GameTestInfo} and writes a JSON report plus a
 * text summary when {@code -Domegaqa.timings=<file.json>} is configured.
 */
public final class GameTestTimings {

    public static final int SCHEMA_VERSION = 1;
    public static final int TOP_SLOWEST_LIMIT = 20;
    public static final String HEAVY_BATCH = "heavy";
    private static final String HEAVY_ANNOTATION_DESC = "Lcom/typeomega/lib/test/HeavyTest;";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    // EN: GameTestInfo.getTick() is package-private; read tickCount via SRG/Mojang reflection instead of adding a
    // mixin.
    // PT: GameTestInfo.getTick() é package-private; lemos tickCount via reflection (SRG/Mojang) em vez de criar mixin.
    // ZH: GameTestInfo.getTick() 为包级私有；通过 SRG/Mojang 反射读取 tickCount，无需添加 mixin。
    private static final @Nullable Field TICK_COUNT_FIELD = findTickCountField();

    public record TimingEntry(
                              String testId,
                              String className,
                              String methodName,
                              String batch,
                              boolean heavyBatch,
                              boolean heavyAnnotated,
                              long ticks,
                              long durationMs,
                              String status) {

        public boolean isHeavy() {
            return heavyBatch || heavyAnnotated;
        }

        public @Nullable String heavyReason() {
            if (heavyBatch && heavyAnnotated) return "batch+@HeavyTest";
            if (heavyBatch) return "batch";
            if (heavyAnnotated) return "@HeavyTest";
            return null;
        }
    }

    public record GroupAggregate(String name, int count, int heavyCount, long totalTicks, long totalDurationMs) {}

    private static final class HeavyIndex {

        final Set<String> heavyClasses;
        final Set<String> heavyMethods;

        private HeavyIndex(Set<String> heavyClasses, Set<String> heavyMethods) {
            this.heavyClasses = heavyClasses;
            this.heavyMethods = heavyMethods;
        }

        boolean isAnnotatedHeavy(String testId, String className, String methodName) {
            if (heavyClasses.contains(className.toLowerCase(Locale.ROOT))) return true;
            if (heavyMethods.contains((className + "." + methodName).toLowerCase(Locale.ROOT))) return true;
            return heavyMethods.contains(testId.toLowerCase(Locale.ROOT));
        }
    }

    private static volatile @Nullable HeavyIndex cachedHeavyIndex;

    private final Path jsonFile;
    private final Path summaryFile;
    private final List<TimingEntry> entries = new ArrayList<>();

    public GameTestTimings(Path jsonFile) {
        this.jsonFile = Objects.requireNonNull(jsonFile, "jsonFile");
        this.summaryFile = summaryPathFor(jsonFile);
    }

    public static @Nullable GameTestTimings createIfConfigured() {
        Path file = OmegaQaProperties.timingsFile();
        return file == null ? null : new GameTestTimings(file);
    }

    public static Path summaryPathFor(Path jsonFile) {
        String name = jsonFile.getFileName().toString();
        String txtName = name.toLowerCase(Locale.ROOT).endsWith(".json") ?
                name.substring(0, name.length() - 5) + ".txt" : name + ".txt";
        Path parent = jsonFile.getParent();
        return parent == null ? Path.of(txtName) : parent.resolve(txtName);
    }

    public synchronized void record(GameTestInfo test, String status) {
        record(fromGameTestInfo(test, status));
    }

    public synchronized void record(TimingEntry entry) {
        Objects.requireNonNull(entry, "entry");
        entries.removeIf(e -> e.testId().equals(entry.testId()));
        entries.add(entry);
    }

    public synchronized List<TimingEntry> entries() {
        return List.copyOf(entries);
    }

    public synchronized void write() {
        List<TimingEntry> snapshot = List.copyOf(entries);
        long now = System.currentTimeMillis();
        try {
            AtomicReportWriter.writeAtomic(jsonFile, toJson(snapshot, now));
            AtomicReportWriter.writeAtomic(summaryFile, toTextSummary(snapshot, now));
            OmegaQA.LOGGER.info("Omega-QA wrote GameTest timings: {} | {}", jsonFile, summaryFile);
        } catch (IOException e) {
            OmegaQA.LOGGER.error("Failed to write Omega-QA timings report", e);
        }
    }

    public static TimingEntry fromGameTestInfo(GameTestInfo test, String status) {
        String testId = test.getTestName();
        int dot = testId.lastIndexOf('.');
        String className = dot > 0 ? testId.substring(0, dot) : testId;
        String methodName = dot > 0 && dot + 1 < testId.length() ? testId.substring(dot + 1) : testId;
        String rawBatch = test.getTestFunction() != null ? test.getTestFunction().getBatchName() : null;
        String batch = rawBatch == null || rawBatch.isBlank() ? "defaultBatch" : rawBatch;
        boolean heavyBatch = HEAVY_BATCH.equalsIgnoreCase(batch);
        boolean heavyAnnotated = heavyIndex().isAnnotatedHeavy(testId, className, methodName);
        long ticks = readTicks(test);
        long durationMs = Math.max(0L, test.getRunTime());
        return new TimingEntry(testId, className, methodName, batch, heavyBatch, heavyAnnotated, ticks, durationMs,
                status);
    }

    public static String toJson(List<TimingEntry> entries, long generatedEpochMs) {
        List<TimingEntry> sorted = sortedBySlowest(entries);
        List<TimingEntry> topSlowest = sorted.stream().limit(TOP_SLOWEST_LIMIT).toList();
        List<GroupAggregate> byClass = aggregate(entries, true);
        List<GroupAggregate> byBatch = aggregate(entries, false);
        List<TimingEntry> heavyTests = sorted.stream().filter(TimingEntry::isHeavy).toList();

        long totalTicks = 0L;
        long totalDurationMs = 0L;
        for (TimingEntry e : entries) {
            totalTicks += Math.max(0L, e.ticks());
            totalDurationMs += Math.max(0L, e.durationMs());
        }

        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("generatedEpochMs", generatedEpochMs);
        root.addProperty("totalTests", entries.size());
        root.addProperty("totalTicks", totalTicks);
        root.addProperty("totalDurationMs", totalDurationMs);
        root.addProperty("heavyCount", heavyTests.size());

        root.add("topSlowest", toEntryArray(topSlowest));
        root.add("byClass", toGroupArray(byClass, "className"));
        root.add("byBatch", toGroupArray(byBatch, "batch"));
        root.add("heavyTests", toEntryArray(heavyTests));
        root.add("tests", toEntryArray(entries));
        return GSON.toJson(root);
    }

    public static String toTextSummary(List<TimingEntry> entries, long generatedEpochMs) {
        List<TimingEntry> sorted = sortedBySlowest(entries);
        List<TimingEntry> topSlowest = sorted.stream().limit(TOP_SLOWEST_LIMIT).toList();
        List<GroupAggregate> byClass = aggregate(entries, true);
        List<GroupAggregate> byBatch = aggregate(entries, false);
        List<TimingEntry> heavyTests = sorted.stream().filter(TimingEntry::isHeavy).toList();

        long totalTicks = 0L;
        long totalDurationMs = 0L;
        for (TimingEntry e : entries) {
            totalTicks += Math.max(0L, e.ticks());
            totalDurationMs += Math.max(0L, e.durationMs());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Omega-QA GameTest Timings Summary\n");
        sb.append("=================================\n");
        sb.append(String.format(Locale.ROOT,
                "Tests: %d | Heavy: %d | Total: %d ms (%d ticks) | GeneratedEpochMs: %d%n%n",
                entries.size(), heavyTests.size(), totalDurationMs, totalTicks, generatedEpochMs));

        sb.append("Top ").append(TOP_SLOWEST_LIMIT).append(" Slowest Tests\n");
        sb.append("--------------------\n");
        if (topSlowest.isEmpty()) {
            sb.append("(none)\n");
        } else {
            int rank = 1;
            for (TimingEntry e : topSlowest) {
                String heavyTag = e.isHeavy() ? " [HEAVY:" + e.heavyReason() + "]" : "";
                sb.append(String.format(Locale.ROOT, "%2d. %6d ms | %5d ticks | %-8s | batch=%s%s | %s%n",
                        rank++, e.durationMs(), e.ticks(), e.status(), e.batch(), heavyTag, e.testId()));
            }
        }
        sb.append('\n');

        sb.append("Total By Class\n");
        sb.append("--------------\n");
        appendGroupTable(sb, byClass);
        sb.append('\n');

        sb.append("Total By Batch\n");
        sb.append("--------------\n");
        appendGroupTable(sb, byBatch);
        sb.append('\n');

        sb.append("Heavy Tests (TestBatches.HEAVY / @HeavyTest)\n");
        sb.append("--------------------------------------------\n");
        if (heavyTests.isEmpty()) {
            sb.append("(none)\n");
        } else {
            for (TimingEntry e : heavyTests) {
                sb.append(String.format(Locale.ROOT, "- %s | batch=%s | reason=%s | %d ms (%d ticks) | %s%n",
                        e.testId(), e.batch(), e.heavyReason(), e.durationMs(), e.ticks(), e.status()));
            }
        }
        return sb.toString();
    }

    private static void appendGroupTable(StringBuilder sb, List<GroupAggregate> groups) {
        if (groups.isEmpty()) {
            sb.append("(none)\n");
            return;
        }
        for (GroupAggregate g : groups) {
            sb.append(String.format(Locale.ROOT, "- %-40s | tests=%3d | heavy=%2d | %7d ms | %6d ticks%n",
                    g.name(), g.count(), g.heavyCount(), g.totalDurationMs(), g.totalTicks()));
        }
    }

    private static JsonArray toEntryArray(List<TimingEntry> list) {
        JsonArray arr = new JsonArray();
        for (TimingEntry e : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("testId", e.testId());
            obj.addProperty("className", e.className());
            obj.addProperty("methodName", e.methodName());
            obj.addProperty("batch", e.batch());
            obj.addProperty("heavy", e.isHeavy());
            if (e.heavyReason() != null) obj.addProperty("heavyReason", e.heavyReason());
            obj.addProperty("ticks", e.ticks());
            obj.addProperty("durationMs", e.durationMs());
            obj.addProperty("status", e.status());
            arr.add(obj);
        }
        return arr;
    }

    private static JsonArray toGroupArray(List<GroupAggregate> list, String keyName) {
        JsonArray arr = new JsonArray();
        for (GroupAggregate g : list) {
            JsonObject obj = new JsonObject();
            obj.addProperty(keyName, g.name());
            obj.addProperty("count", g.count());
            obj.addProperty("heavyCount", g.heavyCount());
            obj.addProperty("totalTicks", g.totalTicks());
            obj.addProperty("totalDurationMs", g.totalDurationMs());
            arr.add(obj);
        }
        return arr;
    }

    public static List<TimingEntry> sortedBySlowest(List<TimingEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparingLong(TimingEntry::durationMs).reversed()
                        .thenComparing(Comparator.comparingLong(TimingEntry::ticks).reversed())
                        .thenComparing(TimingEntry::testId))
                .toList();
    }

    public static List<GroupAggregate> aggregate(List<TimingEntry> entries, boolean byClass) {
        Map<String, long[]> sums = new HashMap<>();
        for (TimingEntry e : entries) {
            String key = byClass ? e.className() : e.batch();
            long[] acc = sums.computeIfAbsent(key, k -> new long[4]);
            acc[0] += 1L;
            acc[1] += e.isHeavy() ? 1L : 0L;
            acc[2] += Math.max(0L, e.ticks());
            acc[3] += Math.max(0L, e.durationMs());
        }
        return sums.entrySet().stream()
                .map(en -> new GroupAggregate(en.getKey(), (int) en.getValue()[0], (int) en.getValue()[1],
                        en.getValue()[2], en.getValue()[3]))
                .sorted(Comparator.comparingLong(GroupAggregate::totalDurationMs).reversed()
                        .thenComparing(Comparator.comparingLong(GroupAggregate::totalTicks).reversed())
                        .thenComparing(GroupAggregate::name))
                .toList();
    }

    private static long readTicks(GameTestInfo test) {
        if (TICK_COUNT_FIELD == null) return 0L;
        try {
            return Math.max(0L, TICK_COUNT_FIELD.getLong(test));
        } catch (IllegalAccessException e) {
            return 0L;
        }
    }

    private static @Nullable Field findTickCountField() {
        try {
            Field f = ObfuscationReflectionHelper.findField(GameTestInfo.class, "f_127606_");
            return f.getType() == long.class ? f : null;
        } catch (Throwable ignored) {
            try {
                Field f = GameTestInfo.class.getDeclaredField("tickCount");
                f.setAccessible(true);
                return f.getType() == long.class ? f : null;
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static HeavyIndex heavyIndex() {
        HeavyIndex current = cachedHeavyIndex;
        if (current != null) return current;
        HeavyIndex built = buildHeavyIndex();
        cachedHeavyIndex = built;
        return built;
    }

    // EN: Scan ModList annotation data for @HeavyTest so TypeOmega Lib 0.8.3+ is detected without a hard compile dep.
    // PT: Varre ModList scan data por @HeavyTest para detectar TypeOmega Lib 0.8.3+ sem dependência rígida de
    // compilação.
    // ZH: 扫描 ModList 注解数据中的 @HeavyTest，无需硬编译依赖即可检测 TypeOmega Lib 0.8.3+。
    private static HeavyIndex buildHeavyIndex() {
        Set<String> classes = new HashSet<>();
        Set<String> methods = new HashSet<>();
        ModList modList = ModList.get();
        if (modList != null) {
            Type heavyType = Type.getType(HEAVY_ANNOTATION_DESC);
            for (ModFileScanData scan : modList.getAllScanData()) {
                Collection<ModFileScanData.AnnotationData> annotations = scan.getAnnotations();
                if (annotations == null) continue;
                for (ModFileScanData.AnnotationData data : annotations) {
                    if (!heavyType.equals(data.annotationType())) continue;
                    String fullClass = data.clazz().getClassName();
                    int dot = fullClass.lastIndexOf('.');
                    String simpleClass = dot >= 0 ? fullClass.substring(dot + 1) : fullClass;
                    if (data.targetType() == ElementType.TYPE) {
                        classes.add(fullClass.toLowerCase(Locale.ROOT));
                        classes.add(simpleClass.toLowerCase(Locale.ROOT));
                    } else if (data.targetType() == ElementType.METHOD) {
                        String member = data.memberName();
                        int paren = member.indexOf('(');
                        String methodName = paren > 0 ? member.substring(0, paren) : member;
                        methods.add((fullClass + "." + methodName).toLowerCase(Locale.ROOT));
                        methods.add((simpleClass + "." + methodName).toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return new HeavyIndex(Set.copyOf(classes), Set.copyOf(methods));
    }
}
