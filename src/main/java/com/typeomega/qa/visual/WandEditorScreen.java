package com.typeomega.qa.visual;

import com.gregtechceu.gtceu.api.gui.GuiTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.typeomega.qa.net.OmegaQaNetwork;
import com.typeomega.qa.net.WandEditActionPacket;
import com.typeomega.qa.spi.WandSelectionData;
import com.typeomega.qa.wand.LabelRole;
import com.typeomega.qa.wand.OmegaWandItem;
import com.typeomega.qa.wand.QaWandTexts;
import com.typeomega.qa.wand.WandDataNbt;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * In-game visual editor for the Omega QA Wand: side panel for semantic labels ({@link LabelRole}), export target,
 * freecam toggle, and 3D viewport gizmo dragging via {@link GizmoRayMath}.
 */
@OnlyIn(Dist.CLIENT)
public class WandEditorScreen extends Screen {

    private static final int PANEL_W = 166;
    private static final int TEXT_DARK = 0xFF2B2B2B;
    private static final int ROW_H = 14;

    private final InteractionHand hand;
    private EditBox namespaceBox;
    private EditBox exportNameBox;
    private EditBox labelNameBox;
    private int panelLeft;
    private int panelTop;
    private int panelH;
    private int listTop;
    private int listRows;

    private boolean rotatingFreecam;
    private @Nullable Direction.Axis draggedAxis;
    private double dragStartParam;
    private int appliedGizmoDelta;

    public WandEditorScreen(InteractionHand hand) {
        super(QaWandTexts.EDITOR_TITLE.component());
        this.hand = hand;
    }

    public boolean isTypingInTextBox() {
        return (namespaceBox != null && namespaceBox.isFocused()) ||
                (exportNameBox != null && exportNameBox.isFocused()) ||
                (labelNameBox != null && labelNameBox.isFocused());
    }

    @Override
    protected void init() {
        panelH = Math.min(246, height - 16);
        panelLeft = width - PANEL_W - 8;
        panelTop = 8;
        WandDataNbt.WandData data = currentData();

        int x = panelLeft + 6;
        int w = PANEL_W - 12;
        int y = panelTop + 18;

        // Tool bar: MOVE | RESIZE | LABEL
        int tw = (w - 4) / 3;
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_TOOL_MOVE.component(),
                b -> ClientOverlayStore.setActiveTool(WandEditorTool.MOVE))
                .bounds(x, y, tw, 14).build());
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_TOOL_RESIZE.component(),
                b -> ClientOverlayStore.setActiveTool(WandEditorTool.RESIZE))
                .bounds(x + tw + 2, y, tw, 14).build());
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_TOOL_MOVE_LABEL.component(),
                b -> ClientOverlayStore.setActiveTool(WandEditorTool.MOVE_LABEL))
                .bounds(x + 2 * (tw + 2), y, tw, 14).build());
        y += 17;

        // Freecam + Nudge buttons
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_FREECAM.component(),
                b -> WandFreecamController.toggle()).bounds(x, y, 68, 14).build());
        int nx = x + 70;
        addRenderableWidget(
                Button.builder(Component.literal("-X"), b -> nudge(-1, 0, 0)).bounds(nx, y, 15, 14).build());
        addRenderableWidget(Button.builder(Component.literal("+X"), b -> nudge(1, 0, 0)).bounds(nx + 16, y, 15, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-Y"), b -> nudge(0, -1, 0)).bounds(nx + 32, y, 15, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+Y"), b -> nudge(0, 1, 0)).bounds(nx + 48, y, 15, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-Z"), b -> nudge(0, 0, -1)).bounds(nx + 64, y, 15, 14)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+Z"), b -> nudge(0, 0, 1)).bounds(nx + 80, y, 15, 14)
                .build());
        y += 18;

        // Namespace & Export Name
        namespaceBox = new EditBox(font, x, y, 56, 14, Component.literal("ns"));
        namespaceBox.setValue(data.exportNamespace());
        addRenderableWidget(namespaceBox);

        exportNameBox = new EditBox(font, x + 59, y, w - 59, 14, Component.literal("name"));
        exportNameBox.setHint(Component.literal("structure_name"));
        exportNameBox.setValue(data.exportName());
        addRenderableWidget(exportNameBox);
        y += 18;

        // Label name box + Add button
        labelNameBox = new EditBox(font, x, y, w - 48, 14, Component.literal("label"));
        labelNameBox.setHint(Component.literal("label_name"));
        addRenderableWidget(labelNameBox);
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_ADD_LABEL.component(), b -> addLabel())
                .bounds(x + w - 46, y, 46, 14).build());
        y += 18;

        // Label management buttons
        int bw = (w - 4) / 3;
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_ROLE.component(), b -> cycleSelectedRole())
                .bounds(x, y, bw, 14).build());
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_RENAME.component(), b -> renameSelectedLabel())
                .bounds(x + bw + 2, y, bw, 14).build());
        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_DELETE.component(), b -> deleteSelectedLabel())
                .bounds(x + 2 * (bw + 2), y, bw, 14).build());
        y += 18;

        listTop = y;
        int bottomReserve = 24;
        listRows = Math.max(2, (panelTop + panelH - bottomReserve - listTop) / ROW_H);

        addRenderableWidget(Button.builder(QaWandTexts.EDITOR_EXPORT.component(), b -> exportNow())
                .bounds(x, panelTop + panelH - 20, w, 15).build());

        if (ClientOverlayStore.activeLabelName() == null && !data.labels().isEmpty()) {
            ClientOverlayStore.setActiveLabelName(data.labels().get(0).name());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        GuiTextures.BACKGROUND.draw(graphics, mouseX, mouseY, panelLeft, panelTop, PANEL_W, panelH);
        graphics.drawString(font, title, panelLeft + 6, panelTop + 6, TEXT_DARK, false);

        int listH = listRows * ROW_H + 4;
        GuiTextures.DISPLAY.draw(graphics, mouseX, mouseY, panelLeft + 5, listTop - 2, PANEL_W - 10, listH);

        WandDataNbt.WandData data = currentData();
        List<WandSelectionData.LabelEntry> labels = data.labels();
        for (int i = 0; i < Math.min(listRows, labels.size()); i++) {
            WandSelectionData.LabelEntry entry = labels.get(i);
            int rowY = listTop + i * ROW_H;
            boolean selected = entry.name().equals(ClientOverlayStore.activeLabelName());
            boolean inside = data.isInsideSelection(entry.relativePos());
            int color = !inside ? 0xFFEF4444 : (selected ? 0xFF22D3EE : 0xFFE2E8F0);
            String line = (selected ? "> " : "  ") + entry.name() + " (" + entry.role() + ")";
            graphics.drawString(font, line, panelLeft + 8, rowY + 2, color, false);
        }

        // Top-left compact HUD bar showing active tool and bounds
        String hud = "Tool: " + ClientOverlayStore.activeTool().name() +
                (data.hasBounds() ? " | " + data.size().getX() + "x" + data.size().getY() + "x" + data.size().getZ() :
                        " | No selection") +
                (WandFreecamController.isActive() ? " | Freecam" : "");
        graphics.drawString(font, hud, 8, 8, 0xFFE2E8F0, true);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // Check label list selection inside side panel
        if (mouseX >= panelLeft && mouseX <= panelLeft + PANEL_W && mouseY >= listTop &&
                mouseY <= listTop + listRows * ROW_H) {
            int idx = (int) ((mouseY - listTop) / ROW_H);
            List<WandSelectionData.LabelEntry> labels = currentData().labels();
            if (idx >= 0 && idx < labels.size()) {
                ClientOverlayStore.setActiveLabelName(labels.get(idx).name());
                return true;
            }
        }

        if (mouseX < panelLeft) {
            if (button == 1) {
                rotatingFreecam = true;
                return true;
            }
            if (button == 0) {
                WandDataNbt.WandData data = currentData();
                if (data.hasBounds()) {
                    GizmoRayMath.Ray ray = currentRay(mouseX, mouseY);
                    BlockPos anchor = HighlightBoxRenderer.resolveGizmoAnchor(data);
                    Vector3f origin = new Vector3f(anchor.getX() + 0.5f, anchor.getY() + 0.5f, anchor.getZ() + 0.5f);
                    Direction.Axis picked = GizmoRayMath.pickClosestAxis(ray, origin, 0.75);
                    if (picked != null) {
                        draggedAxis = picked;
                        dragStartParam = GizmoRayMath.closestApproachOnAxis(ray.origin(), ray.direction(), origin,
                                GizmoRayMath.axisVector(picked)).axisParam();
                        appliedGizmoDelta = 0;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (rotatingFreecam && button == 1) {
            if (!WandFreecamController.isActive()) WandFreecamController.enable();
            WandFreecamController.rotate(dragX * 0.35, dragY * 0.35);
            return true;
        }
        if (draggedAxis != null && button == 0) {
            WandDataNbt.WandData data = currentData();
            if (!data.hasBounds()) return true;
            GizmoRayMath.Ray ray = currentRay(mouseX, mouseY);
            BlockPos anchor = HighlightBoxRenderer.resolveGizmoAnchor(data);
            Vector3f origin = new Vector3f(anchor.getX() + 0.5f, anchor.getY() + 0.5f, anchor.getZ() + 0.5f);
            double nowParam = GizmoRayMath.closestApproachOnAxis(ray.origin(), ray.direction(), origin,
                    GizmoRayMath.axisVector(draggedAxis)).axisParam();
            int snappedTotal = GizmoRayMath.snapToInt(nowParam - dragStartParam);
            int step = snappedTotal - appliedGizmoDelta;
            if (step != 0) {
                appliedGizmoDelta = snappedTotal;
                applyAxisStep(draggedAxis, step);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) rotatingFreecam = false;
        if (button == 0) draggedAxis = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        WandFreecamController.disable();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private GizmoRayMath.Ray currentRay(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        var camPos = mc.gameRenderer.getMainCamera().getPosition();
        return GizmoRayMath.unprojectScreenRay(mouseX, mouseY, width, height,
                OmegaLevelOverlayRenderer.lastViewProjMatrix(),
                new Vector3f((float) camPos.x, (float) camPos.y, (float) camPos.z));
    }

    private void applyAxisStep(Direction.Axis axis, int step) {
        int dx = axis == Direction.Axis.X ? step : 0;
        int dy = axis == Direction.Axis.Y ? step : 0;
        int dz = axis == Direction.Axis.Z ? step : 0;
        nudge(dx, dy, dz);
    }

    private void nudge(int dx, int dy, int dz) {
        switch (ClientOverlayStore.activeTool()) {
            case MOVE -> OmegaQaNetwork.sendToServer(WandEditActionPacket.moveSelection(hand, dx, dy, dz));
            case RESIZE -> {
                Direction dir = directionFromDelta(dx, dy, dz);
                if (dir != null) {
                    OmegaQaNetwork.sendToServer(WandEditActionPacket.resizeFace(hand, dir, 1));
                }
            }
            case MOVE_LABEL -> {
                String label = ClientOverlayStore.activeLabelName();
                if (label != null) {
                    OmegaQaNetwork.sendToServer(WandEditActionPacket.moveLabel(hand, label, dx, dy, dz));
                }
            }
        }
    }

    private static @Nullable Direction directionFromDelta(int dx, int dy, int dz) {
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dy > 0) return Direction.UP;
        if (dy < 0) return Direction.DOWN;
        if (dz > 0) return Direction.SOUTH;
        if (dz < 0) return Direction.NORTH;
        return null;
    }

    private void addLabel() {
        WandDataNbt.WandData data = currentData();
        String typed = labelNameBox.getValue().trim();
        String name = typed.isEmpty() ? "label_" + (data.labels().size() + 1) : typed;
        BlockPos rel = BlockPos.ZERO;
        Direction facing = null;
        Minecraft mc = Minecraft.getInstance();
        if (data.hasBounds() && mc.hitResult instanceof BlockHitResult bhr &&
                bhr.getType() == HitResult.Type.BLOCK) {
            rel = bhr.getBlockPos().subtract(data.minPos());
            facing = bhr.getDirection();
        }
        String role = LabelRole.fromId(name).id();
        OmegaQaNetwork.sendToServer(WandEditActionPacket.upsertLabel(hand, name, role, rel, facing));
        ClientOverlayStore.setActiveLabelName(name);
    }

    private void cycleSelectedRole() {
        String selected = ClientOverlayStore.activeLabelName();
        if (selected == null) return;
        for (WandSelectionData.LabelEntry entry : currentData().labels()) {
            if (entry.name().equals(selected)) {
                LabelRole next = LabelRole.fromId(entry.role()).next();
                OmegaQaNetwork.sendToServer(WandEditActionPacket.setLabelRole(hand, selected, next.id()));
                return;
            }
        }
    }

    private void renameSelectedLabel() {
        String selected = ClientOverlayStore.activeLabelName();
        String nextName = labelNameBox.getValue().trim();
        if (selected == null || nextName.isEmpty()) return;
        OmegaQaNetwork.sendToServer(WandEditActionPacket.renameLabel(hand, selected, nextName));
        ClientOverlayStore.setActiveLabelName(nextName);
    }

    private void deleteSelectedLabel() {
        String selected = ClientOverlayStore.activeLabelName();
        if (selected == null) return;
        OmegaQaNetwork.sendToServer(WandEditActionPacket.deleteLabel(hand, selected));
        ClientOverlayStore.setActiveLabelName(null);
    }

    private void exportNow() {
        String ns = namespaceBox.getValue().trim();
        String name = exportNameBox.getValue().trim();
        if (name.isEmpty()) name = "exported_structure";
        OmegaQaNetwork.sendToServer(WandEditActionPacket.exportNow(hand, ns, name));
    }

    private WandDataNbt.WandData currentData() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return WandDataNbt.WandData.empty();
        ItemStack stack = mc.player.getItemInHand(hand);
        if (stack.getItem() instanceof OmegaWandItem) {
            return WandDataNbt.read(stack);
        }
        return WandDataNbt.WandData.empty();
    }
}
