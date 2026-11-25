package com.typeomega.qa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;

import com.typeomega.lib.ui.report.Report;
import com.typeomega.lib.ui.report.ReportScreen;
import com.typeomega.qa.net.SyncWandStatePacket;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaReportSnapshot;
import com.typeomega.qa.spi.PerfBaselineRecord;
import com.typeomega.qa.spi.TestCaseRecord;
import com.typeomega.qa.wand.OmegaWandItem;
import com.typeomega.qa.wand.QaWandTexts;
import com.typeomega.qa.wand.WandDataNbt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client-only lifecycle bridge invoked exclusively behind {@code Dist.CLIENT} {@code DistExecutor} guards so
 * dedicated {@code gameTestServer} runs never touch {@code net.minecraft.client.*}.
 */
public final class ClientVisualHooks {

    private ClientVisualHooks() {}

    public static void init() {
        OmegaWandItem.setClientEditorOpener(ClientVisualHooks::openWandEditor);
        MinecraftForge.EVENT_BUS.addListener(OmegaLevelOverlayRenderer::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(WandFreecamController::onMovementInput);
        MinecraftForge.EVENT_BUS.addListener(WandFreecamController::onComputeCameraAngles);
        MinecraftForge.EVENT_BUS.addListener(WandFreecamController::onClientTick);
    }

    public static void openWandEditor(InteractionHand hand) {
        Minecraft.getInstance().setScreen(new WandEditorScreen(hand));
    }

    public static void applyWandSync(SyncWandStatePacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getItemInHand(pkt.hand());
        if (stack.getItem() instanceof OmegaWandItem) {
            WandDataNbt.write(stack, WandDataNbt.WandData.fromSelectionData(pkt.selection(), pkt.nextCorner()));
            if (pkt.openEditor()) {
                openWandEditor(pkt.hand());
            }
        }
    }

    public static void applyLocalDiagnostic(FailureDiagnostic diag) {
        ClientOverlayStore.putDiagnostic(diag);
    }

    public static void clearLocalTest(String testId) {
        ClientOverlayStore.removeDiagnostic(testId);
    }

    public static void clearAllLocal() {
        ClientOverlayStore.clearAll();
    }

    public static void openReportScreen(OmegaReportSnapshot snapshot) {
        Report report = toUiReport(snapshot);
        Minecraft.getInstance().setScreen(new ReportScreen(report, visibleGroups -> {
            // EN: "Show boxes" enables failure diff overlays and populates diagnostics for visible fail positions.
            // PT: "Mostrar caixas" ativa os overlays de falha e popula diagnósticos para as posições visíveis.
            // ZH: “显示方框”启用失败差异叠加层，并为可见的失败位置填充诊断信息。
            ClientOverlayStore.setShowFailureBoxes(true);
            for (TestCaseRecord t : snapshot.tests()) {
                if (t.failPos() != null && (t.status() == FailureDiagnostic.TestStatus.FAILED ||
                        t.status() == FailureDiagnostic.TestStatus.ERROR ||
                        t.status() == FailureDiagnostic.TestStatus.TIMED_OUT)) {
                    ClientOverlayStore.putDiagnostic(new FailureDiagnostic(
                            t.testId(), t.failPos(), new Vec3i(1, 1, 1), t.failPos(),
                            null, null, t.message() == null ? "" : t.message(), t.status()));
                }
            }
        }));
    }

    private static Report toUiReport(OmegaReportSnapshot snapshot) {
        List<Report.Line> failedLines = new ArrayList<>();
        List<Report.Line> skippedLines = new ArrayList<>();
        List<Report.Line> passedLines = new ArrayList<>();
        for (TestCaseRecord t : snapshot.tests()) {
            String summary = t.className() + "." + t.methodName() +
                    (t.failTick() != null ? " [t=" + t.failTick() + "]" : "") +
                    (t.message() != null ? " — " + t.message() : "");
            Report.Line line = new Report.Line(Component.literal(summary), t.failPos());
            switch (t.status()) {
                case FAILED, ERROR, TIMED_OUT -> failedLines.add(line);
                case SKIPPED -> skippedLines.add(line);
                case PASSED, RUNNING -> passedLines.add(line);
            }
        }
        List<Report.Line> benchLines = new ArrayList<>();
        for (PerfBaselineRecord b : snapshot.benchmarks()) {
            String line = String.format(Locale.ROOT, "%s (%dt): avg=%.3f p95=%.3f p99=%.3f max=%.3f ms | heap=%.2fMB",
                    b.benchmarkId(), b.ticks(), b.avgMs(), b.p95Ms(), b.p99Ms(), b.maxMs(), b.heapDeltaMb());
            benchLines.add(new Report.Line(Component.literal(line), (BlockPos) null));
        }

        return Report.builder(QaWandTexts.REPORT_TITLE.component())
                .group(QaWandTexts.REPORT_GROUP_FAILED.component(), 0xEF4444, failedLines)
                .group(QaWandTexts.REPORT_GROUP_SKIPPED.component(), 0x38BDF8, skippedLines)
                .group(QaWandTexts.REPORT_GROUP_PASSED.component(), 0x4ADE80, passedLines)
                .group(QaWandTexts.REPORT_GROUP_BENCH.component(), 0xFACC15, benchLines)
                .build();
    }
}
