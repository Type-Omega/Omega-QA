package com.typeomega.qa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.typeomega.qa.spi.FailureDiagnostic;

/**
 * Renders vertical status beacons at active/completed GameTest cells colored by {@link FailureDiagnostic.TestStatus}.
 */
@OnlyIn(Dist.CLIENT)
public final class DebugBeaconRenderer {

    private DebugBeaconRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long gameTime = mc.level.getGameTime();

        for (FailureDiagnostic diag : ClientOverlayStore.diagnostics()) {
            BlockPos origin = diag.cellOrigin();
            Vec3i size = diag.cellSize();
            double cx = origin.getX() + Math.max(1, size.getX()) * 0.5;
            double cy = origin.getY() + Math.max(1, size.getY());
            double cz = origin.getZ() + Math.max(1, size.getZ()) * 0.5;
            float[] rgb = colorForStatus(diag.status());

            poseStack.pushPose();
            poseStack.translate(cx - 0.5, cy, cz - 0.5);
            BeaconRenderer.renderBeaconBeam(poseStack, buffer, BeaconRenderer.BEAM_LOCATION,
                    partialTick, 1.0f, gameTime, 0, 96, rgb, 0.16f, 0.22f);
            poseStack.popPose();
        }
    }

    public static float[] colorForStatus(FailureDiagnostic.TestStatus status) {
        return switch (status) {
            case RUNNING -> new float[] { 0.65f, 0.65f, 0.65f };
            case PASSED -> new float[] { 0.15f, 0.85f, 0.30f };
            case FAILED -> new float[] { 0.94f, 0.27f, 0.27f };
            case ERROR -> new float[] { 0.85f, 0.20f, 0.85f };
            case TIMED_OUT -> new float[] { 0.96f, 0.58f, 0.12f };
            case SKIPPED -> new float[] { 0.38f, 0.75f, 0.96f };
        };
    }
}
