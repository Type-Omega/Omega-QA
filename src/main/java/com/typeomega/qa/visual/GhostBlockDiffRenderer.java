package com.typeomega.qa.visual;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import com.typeomega.qa.spi.FailureDiagnostic;

/**
 * Renders {@link FailureDiagnostic} world diffs: a red translucent box ({@code alpha 0.45}) at {@code failPos} and
 * a green translucent highlight + ghost block render for {@code expectedState}.
 */
@OnlyIn(Dist.CLIENT)
public final class GhostBlockDiffRenderer {

    private GhostBlockDiffRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer) {
        if (!ClientOverlayStore.showFailureBoxes()) return;

        var blockRenderer = Minecraft.getInstance().getBlockRenderer();
        for (FailureDiagnostic diag : ClientOverlayStore.diagnostics()) {
            if (diag.status() != FailureDiagnostic.TestStatus.FAILED &&
                    diag.status() != FailureDiagnostic.TestStatus.ERROR &&
                    diag.status() != FailureDiagnostic.TestStatus.TIMED_OUT) {
                continue;
            }
            BlockPos failPos = diag.failPos();
            if (failPos == null) continue;

            // Red translucent cube over the wrong/missing block
            HighlightBoxRenderer.renderBoxOutline(poseStack, buffer, failPos, failPos, 0.94f, 0.22f, 0.22f, 0.95f);
            HighlightBoxRenderer.renderTranslucentFill(poseStack, buffer, failPos, failPos, 0.94f, 0.22f, 0.22f, 0.45f);

            BlockState expected = diag.expectedState();
            if (expected != null && !expected.isAir()) {
                // EN: Render expected block slightly inset inside a green frame so both red actual and green expected
                // remain visible simultaneously.
                // PT: Renderiza o bloco esperado levemente reduzido com moldura verde para mostrar ambos ao mesmo
                // tempo.
                // ZH: 在绿色框内略微缩进渲染期望方块，使红色错误与绿色期望同时清晰可见。
                HighlightBoxRenderer.renderTranslucentFill(poseStack, buffer, failPos, failPos, 0.20f, 0.88f, 0.40f,
                        0.22f);
                poseStack.pushPose();
                poseStack.translate(failPos.getX() + 0.15, failPos.getY() + 0.15, failPos.getZ() + 0.15);
                poseStack.scale(0.70f, 0.70f, 0.70f);
                blockRenderer.renderSingleBlock(expected, poseStack, buffer, LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.translucent());
                poseStack.popPose();
            }
        }
    }
}
