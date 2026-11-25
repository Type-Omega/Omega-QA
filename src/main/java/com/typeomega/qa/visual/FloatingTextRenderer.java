package com.typeomega.qa.visual;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.WandSelectionData;
import com.typeomega.qa.wand.LabelRole;
import com.typeomega.qa.wand.WandDataNbt;
import org.jetbrains.annotations.Nullable;

/**
 * Renders billboarded world-space labels (up to 32 blocks) for GameTest diagnostics and Omega QA Wand markers.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingTextRenderer {

    private static final double MAX_DISTANCE_SQ = 32.0 * 32.0;

    private FloatingTextRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer, Camera camera,
                              @Nullable WandDataNbt.WandData wandData) {
        Vec3 camPos = camera.getPosition();

        if (wandData != null && wandData.hasBounds()) {
            BlockPos min = wandData.minPos();
            for (WandSelectionData.LabelEntry entry : wandData.labels()) {
                BlockPos abs = min.offset(entry.relativePos());
                double wx = abs.getX() + 0.5;
                double wy = abs.getY() + 1.25;
                double wz = abs.getZ() + 0.5;
                if (camPos.distanceToSqr(wx, wy, wz) > MAX_DISTANCE_SQ) continue;
                int rgb = wandData.isInsideSelection(entry.relativePos()) ?
                        LabelRole.fromId(entry.role()).colorRgb() : 0xFFEF4444;
                drawBillboard(poseStack, buffer, camera, wx, wy, wz,
                        entry.name() + " [" + entry.role() + "]", rgb);
            }
        }

        for (FailureDiagnostic diag : ClientOverlayStore.diagnostics()) {
            BlockPos anchor = diag.failPos() != null ? diag.failPos() : diag.cellOrigin();
            Vec3i size = diag.cellSize();
            double wx = anchor.getX() + (diag.failPos() != null ? 0.5 : Math.max(1, size.getX()) * 0.5);
            double wy = anchor.getY() + (diag.failPos() != null ? 1.4 : Math.max(1, size.getY()) + 0.8);
            double wz = anchor.getZ() + (diag.failPos() != null ? 0.5 : Math.max(1, size.getZ()) * 0.5);
            if (camPos.distanceToSqr(wx, wy, wz) > MAX_DISTANCE_SQ) continue;
            String text = diag.testId() + " [" + diag.status().name() + "]";
            drawBillboard(poseStack, buffer, camera, wx, wy, wz, text, colorIntForStatus(diag.status()));
        }
    }

    private static void drawBillboard(PoseStack poseStack, MultiBufferSource buffer, Camera camera,
                                      double x, double y, double z, String text, int colorArgb) {
        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025f, -0.025f, 0.025f);

        float xOffset = -font.width(text) * 0.5f;
        font.drawInBatch(text, xOffset, 0f, colorArgb, false, poseStack.last().pose(), buffer,
                Font.DisplayMode.SEE_THROUGH, 0x70000000, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static int colorIntForStatus(FailureDiagnostic.TestStatus status) {
        return switch (status) {
            case RUNNING -> 0xFFD4D4D8;
            case PASSED -> 0xFF4ADE80;
            case FAILED -> 0xFFEF4444;
            case ERROR -> 0xFFE879F9;
            case TIMED_OUT -> 0xFFFB923C;
            case SKIPPED -> 0xFF38BDF8;
        };
    }
}
