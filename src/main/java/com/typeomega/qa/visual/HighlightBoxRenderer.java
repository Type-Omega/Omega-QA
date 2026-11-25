package com.typeomega.qa.visual;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.typeomega.qa.spi.WandSelectionData;
import com.typeomega.qa.wand.LabelRole;
import com.typeomega.qa.wand.WandDataNbt;

/**
 * Renders the Omega QA Wand selection wireframe, semantic label boxes (cyan/role color inside selection, red if a label
 * sits outside resized bounds), and 3D RGB gizmo axes.
 */
@OnlyIn(Dist.CLIENT)
public final class HighlightBoxRenderer {

    private HighlightBoxRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffer, WandDataNbt.WandData data,
                              boolean editorOpen) {
        if (data.pos1() != null && data.pos2() == null) {
            renderBoxOutline(poseStack, buffer, data.pos1(), data.pos1(), 0.98f, 0.78f, 0.15f, 0.95f);
            return;
        }
        if (!data.hasBounds()) return;

        BlockPos min = data.minPos();
        BlockPos max = data.maxPos();
        renderBoxOutline(poseStack, buffer, min, max, 0.13f, 0.83f, 0.93f, 0.95f);
        renderTranslucentFill(poseStack, buffer, min, max, 0.13f, 0.83f, 0.93f, 0.08f);

        for (WandSelectionData.LabelEntry entry : data.labels()) {
            BlockPos abs = min.offset(entry.relativePos());
            boolean inside = data.isInsideSelection(entry.relativePos());
            if (!inside) {
                // EN: Highlight labels outside selection bounds in bright red so resize drift is immediately visible.
                // PT: Destaca labels fora dos limites da seleção em vermelho vivo para evidenciar desalinhamentos.
                // ZH: 将超出选区边界的标记高亮显示为亮红色，便于立即发现尺寸调整偏移。
                renderBoxOutline(poseStack, buffer, abs, abs, 0.94f, 0.27f, 0.27f, 1.0f);
                renderTranslucentFill(poseStack, buffer, abs, abs, 0.94f, 0.27f, 0.27f, 0.28f);
            } else {
                int rgb = LabelRole.fromId(entry.role()).colorRgb();
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                boolean selected = entry.name().equals(ClientOverlayStore.activeLabelName());
                float alpha = selected ? 1.0f : 0.85f;
                renderBoxOutline(poseStack, buffer, abs, abs, r, g, b, alpha);
                renderTranslucentFill(poseStack, buffer, abs, abs, r, g, b, selected ? 0.30f : 0.16f);
            }
        }

        if (editorOpen) {
            renderGizmoAxes(poseStack, buffer, data);
        }
    }

    private static void renderGizmoAxes(PoseStack poseStack, MultiBufferSource buffer, WandDataNbt.WandData data) {
        BlockPos anchor = resolveGizmoAnchor(data);
        double cx = anchor.getX() + 0.5;
        double cy = anchor.getY() + 0.5;
        double cz = anchor.getZ() + 0.5;
        double len = 2.0;

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        PoseStack.Pose pose = poseStack.last();
        // X (Red)
        lines.vertex(pose.pose(), (float) cx, (float) cy, (float) cz).color(1f, 0.25f, 0.25f, 1f)
                .normal(pose.normal(), 1f, 0f, 0f).endVertex();
        lines.vertex(pose.pose(), (float) (cx + len), (float) cy, (float) cz).color(1f, 0.25f, 0.25f, 1f)
                .normal(pose.normal(), 1f, 0f, 0f).endVertex();
        // Y (Green)
        lines.vertex(pose.pose(), (float) cx, (float) cy, (float) cz).color(0.25f, 1f, 0.35f, 1f)
                .normal(pose.normal(), 0f, 1f, 0f).endVertex();
        lines.vertex(pose.pose(), (float) cx, (float) (cy + len), (float) cz).color(0.25f, 1f, 0.35f, 1f)
                .normal(pose.normal(), 0f, 1f, 0f).endVertex();
        // Z (Blue)
        lines.vertex(pose.pose(), (float) cx, (float) cy, (float) cz).color(0.30f, 0.60f, 1f, 1f)
                .normal(pose.normal(), 0f, 0f, 1f).endVertex();
        lines.vertex(pose.pose(), (float) cx, (float) cy, (float) (cz + len)).color(0.30f, 0.60f, 1f, 1f)
                .normal(pose.normal(), 0f, 0f, 1f).endVertex();
    }

    public static BlockPos resolveGizmoAnchor(WandDataNbt.WandData data) {
        BlockPos min = data.minPos();
        if (min == null) return BlockPos.ZERO;
        if (ClientOverlayStore.activeTool() == WandEditorTool.MOVE_LABEL &&
                ClientOverlayStore.activeLabelName() != null) {
            for (WandSelectionData.LabelEntry entry : data.labels()) {
                if (entry.name().equals(ClientOverlayStore.activeLabelName())) {
                    return min.offset(entry.relativePos());
                }
            }
        }
        Vec3i size = data.size();
        return min.offset(size.getX() / 2, size.getY() / 2, size.getZ() / 2);
    }

    public static void renderBoxOutline(PoseStack poseStack, MultiBufferSource buffer, BlockPos min, BlockPos max,
                                        float r, float g, float b, float a) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, consumer,
                min.getX() - 0.004, min.getY() - 0.004, min.getZ() - 0.004,
                max.getX() + 1.004, max.getY() + 1.004, max.getZ() + 1.004,
                r, g, b, a);
    }

    public static void renderTranslucentFill(PoseStack poseStack, MultiBufferSource buffer, BlockPos min, BlockPos max,
                                             float r, float g, float b, float a) {
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugFilledBox());
        LevelRenderer.addChainedFilledBoxVertices(poseStack, consumer,
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0,
                r, g, b, a);
    }
}
