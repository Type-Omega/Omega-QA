package com.typeomega.qa.visual;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import com.typeomega.qa.wand.OmegaWandItem;
import com.typeomega.qa.wand.WandDataNbt;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Coordinates all in-world Omega-QA visual overlays during {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS}
 * using modern MC 1.20.1 {@link PoseStack} and {@link MultiBufferSource}.
 */
@OnlyIn(Dist.CLIENT)
public final class OmegaLevelOverlayRenderer {

    private static volatile Matrix4f lastViewProj = new Matrix4f();

    private OmegaLevelOverlayRenderer() {}

    public static Matrix4f lastViewProjMatrix() {
        return new Matrix4f(lastViewProj);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        // EN: Snapshot combined Projection * ModelView matrix for GizmoRayMath cursor unprojection in WandEditorScreen.
        // PT: Captura a matriz combinada Projection * ModelView para GizmoRayMath no WandEditorScreen.
        // ZH: 捕获 Projection * ModelView 组合矩阵，供 WandEditorScreen 的 GizmoRayMath 进行光标反投影。
        Matrix4f modelView = new Matrix4f(poseStack.last().pose());
        lastViewProj = new Matrix4f(event.getProjectionMatrix()).mul(modelView);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        WandDataNbt.WandData wandData = findHeldWandData(player);
        boolean editorOpen = mc.screen instanceof WandEditorScreen;

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        if (wandData != null) {
            HighlightBoxRenderer.render(poseStack, bufferSource, wandData, editorOpen);
        }
        DebugBeaconRenderer.render(poseStack, bufferSource, event.getPartialTick());
        GhostBlockDiffRenderer.render(poseStack, bufferSource);
        FloatingTextRenderer.render(poseStack, bufferSource, camera, wandData);

        poseStack.popPose();
        bufferSource.endBatch();
    }

    public static @Nullable WandDataNbt.WandData findHeldWandData(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof OmegaWandItem) return WandDataNbt.read(main);
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof OmegaWandItem) return WandDataNbt.read(off);
        return null;
    }
}
