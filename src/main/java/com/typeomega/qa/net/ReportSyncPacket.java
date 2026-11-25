package com.typeomega.qa.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaReportSnapshot;
import com.typeomega.qa.spi.PerfBaselineRecord;
import com.typeomega.qa.spi.TestCaseRecord;
import com.typeomega.qa.visual.ClientVisualHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C packet delivering the latest {@link OmegaReportSnapshot} to open {@code ReportScreen} on the client.
 */
public record ReportSyncPacket(OmegaReportSnapshot snapshot) {

    private static final int MAX_TESTS_SENT = 512;
    private static final int MAX_BENCHES_SENT = 128;

    public static void encode(ReportSyncPacket msg, FriendlyByteBuf buf) {
        OmegaReportSnapshot s = msg.snapshot;
        buf.writeLong(s.generatedEpochMs());
        buf.writeUtf(s.status(), 32);
        buf.writeVarInt(s.exitCode());
        buf.writeVarInt(s.total());
        buf.writeVarInt(s.passed());
        buf.writeVarInt(s.failed());
        buf.writeVarInt(s.errors());
        buf.writeVarInt(s.skipped());

        List<TestCaseRecord> tests = s.tests().size() > MAX_TESTS_SENT ? s.tests().subList(0, MAX_TESTS_SENT) :
                s.tests();
        buf.writeVarInt(tests.size());
        for (TestCaseRecord t : tests) {
            buf.writeUtf(t.testId(), 256);
            buf.writeUtf(t.className(), 256);
            buf.writeUtf(t.methodName(), 128);
            buf.writeEnum(t.status());
            buf.writeLong(t.durationMs());
            buf.writeBoolean(t.failTick() != null);
            if (t.failTick() != null) buf.writeVarInt(t.failTick());
            buf.writeUtf(t.invariantName() == null ? "" : t.invariantName(), 128);
            buf.writeUtf(t.message() == null ? "" : t.message(), 512);
            buf.writeBoolean(t.failPos() != null);
            if (t.failPos() != null) buf.writeBlockPos(t.failPos());
            buf.writeUtf(t.expectedBlock() == null ? "" : t.expectedBlock(), 256);
            buf.writeUtf(t.actualBlock() == null ? "" : t.actualBlock(), 256);
        }

        List<PerfBaselineRecord> benches = s.benchmarks().size() > MAX_BENCHES_SENT ?
                s.benchmarks().subList(0, MAX_BENCHES_SENT) : s.benchmarks();
        buf.writeVarInt(benches.size());
        for (PerfBaselineRecord b : benches) {
            buf.writeUtf(b.benchmarkId(), 128);
            buf.writeVarInt(b.ticks());
            buf.writeDouble(b.avgMs());
            buf.writeDouble(b.p50Ms());
            buf.writeDouble(b.p95Ms());
            buf.writeDouble(b.p99Ms());
            buf.writeDouble(b.maxMs());
            buf.writeDouble(b.heapDeltaMb());
            buf.writeLong(b.gcPauseMsDelta());
        }
    }

    public static ReportSyncPacket decode(FriendlyByteBuf buf) {
        long epoch = buf.readLong();
        String status = buf.readUtf(32);
        int exit = buf.readVarInt();
        int total = buf.readVarInt();
        int passed = buf.readVarInt();
        int failed = buf.readVarInt();
        int errors = buf.readVarInt();
        int skipped = buf.readVarInt();

        int testCount = buf.readVarInt();
        List<TestCaseRecord> tests = new ArrayList<>(testCount);
        for (int i = 0; i < testCount; i++) {
            String testId = buf.readUtf(256);
            String cls = buf.readUtf(256);
            String method = buf.readUtf(128);
            FailureDiagnostic.TestStatus st = buf.readEnum(FailureDiagnostic.TestStatus.class);
            long dur = buf.readLong();
            Integer failTick = buf.readBoolean() ? buf.readVarInt() : null;
            String inv = buf.readUtf(128);
            String msg = buf.readUtf(512);
            BlockPos failPos = buf.readBoolean() ? buf.readBlockPos() : null;
            String expected = buf.readUtf(256);
            String actual = buf.readUtf(256);
            tests.add(new TestCaseRecord(testId, cls, method, st, dur, failTick,
                    inv.isEmpty() ? null : inv, msg.isEmpty() ? null : msg, failPos,
                    expected.isEmpty() ? null : expected, actual.isEmpty() ? null : actual, List.of()));
        }

        int benchCount = buf.readVarInt();
        List<PerfBaselineRecord> benches = new ArrayList<>(benchCount);
        for (int i = 0; i < benchCount; i++) {
            benches.add(new PerfBaselineRecord(
                    buf.readUtf(128), buf.readVarInt(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readLong(), List.of()));
        }

        return new ReportSyncPacket(new OmegaReportSnapshot(epoch, status, exit, total, passed, failed, errors,
                skipped, tests, benches, List.of()));
    }

    public static void handle(ReportSyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientVisualHooks.openReportScreen(msg.snapshot)));
        ctx.setPacketHandled(true);
    }
}
