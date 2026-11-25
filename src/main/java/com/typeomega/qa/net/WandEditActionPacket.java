package com.typeomega.qa.net;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.typeomega.qa.spi.WandSelectionData;
import com.typeomega.qa.wand.LabelRole;
import com.typeomega.qa.wand.OmegaWandItem;
import com.typeomega.qa.wand.QaWandTexts;
import com.typeomega.qa.wand.StructureExportService;
import com.typeomega.qa.wand.WandDataNbt;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * C2S packet carrying visual editor modifications (move/resize selection, add/rename/move/role/delete label,
 * update export target, or trigger immediate structure export).
 */
public record WandEditActionPacket(
                                   InteractionHand hand,
                                   ActionType action,
                                   int dx,
                                   int dy,
                                   int dz,
                                   @Nullable Direction direction,
                                   String primaryText,
                                   String secondaryText,
                                   BlockPos pos) {

    public enum ActionType {
        MOVE_SELECTION,
        RESIZE_FACE,
        MOVE_LABEL,
        ADD_OR_UPDATE_LABEL,
        RENAME_LABEL,
        DELETE_LABEL,
        SET_LABEL_ROLE,
        SET_EXPORT_INFO,
        EXPORT_NOW
    }

    public static WandEditActionPacket moveSelection(InteractionHand hand, int dx, int dy, int dz) {
        return new WandEditActionPacket(hand, ActionType.MOVE_SELECTION, dx, dy, dz, null, "", "", BlockPos.ZERO);
    }

    public static WandEditActionPacket resizeFace(InteractionHand hand, Direction face, int delta) {
        return new WandEditActionPacket(hand, ActionType.RESIZE_FACE, delta, 0, 0, face, "", "", BlockPos.ZERO);
    }

    public static WandEditActionPacket moveLabel(InteractionHand hand, String labelName, int dx, int dy, int dz) {
        return new WandEditActionPacket(hand, ActionType.MOVE_LABEL, dx, dy, dz, null, labelName, "", BlockPos.ZERO);
    }

    public static WandEditActionPacket upsertLabel(InteractionHand hand, String name, String role, BlockPos relPos,
                                                   @Nullable Direction facing) {
        return new WandEditActionPacket(hand, ActionType.ADD_OR_UPDATE_LABEL, 0, 0, 0, facing, name, role, relPos);
    }

    public static WandEditActionPacket renameLabel(InteractionHand hand, String oldName, String newName) {
        return new WandEditActionPacket(hand, ActionType.RENAME_LABEL, 0, 0, 0, null, oldName, newName, BlockPos.ZERO);
    }

    public static WandEditActionPacket deleteLabel(InteractionHand hand, String name) {
        return new WandEditActionPacket(hand, ActionType.DELETE_LABEL, 0, 0, 0, null, name, "", BlockPos.ZERO);
    }

    public static WandEditActionPacket setLabelRole(InteractionHand hand, String name, String role) {
        return new WandEditActionPacket(hand, ActionType.SET_LABEL_ROLE, 0, 0, 0, null, name, role, BlockPos.ZERO);
    }

    public static WandEditActionPacket setExportInfo(InteractionHand hand, String namespace, String name) {
        return new WandEditActionPacket(hand, ActionType.SET_EXPORT_INFO, 0, 0, 0, null, namespace, name,
                BlockPos.ZERO);
    }

    public static WandEditActionPacket exportNow(InteractionHand hand, String namespace, String name) {
        return new WandEditActionPacket(hand, ActionType.EXPORT_NOW, 0, 0, 0, null, namespace, name, BlockPos.ZERO);
    }

    public static void encode(WandEditActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.dx);
        buf.writeVarInt(msg.dy);
        buf.writeVarInt(msg.dz);
        buf.writeBoolean(msg.direction != null);
        if (msg.direction != null) buf.writeEnum(msg.direction);
        buf.writeUtf(msg.primaryText == null ? "" : msg.primaryText, 128);
        buf.writeUtf(msg.secondaryText == null ? "" : msg.secondaryText, 128);
        buf.writeBlockPos(msg.pos == null ? BlockPos.ZERO : msg.pos);
    }

    public static WandEditActionPacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        ActionType action = buf.readEnum(ActionType.class);
        int dx = buf.readVarInt();
        int dy = buf.readVarInt();
        int dz = buf.readVarInt();
        Direction dir = buf.readBoolean() ? buf.readEnum(Direction.class) : null;
        String primary = buf.readUtf(128);
        String secondary = buf.readUtf(128);
        BlockPos pos = buf.readBlockPos();
        return new WandEditActionPacket(hand, action, dx, dy, dz, dir, primary, secondary, pos);
    }

    public static void handle(WandEditActionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            ItemStack stack = player.getItemInHand(msg.hand);
            if (!(stack.getItem() instanceof OmegaWandItem)) return;

            WandDataNbt.WandData data = WandDataNbt.read(stack);
            WandDataNbt.WandData updated = applyAction(player, data, msg);
            WandDataNbt.write(stack, updated);
            OmegaQaNetwork.sendToPlayer(player,
                    new SyncWandStatePacket(msg.hand, updated.toSelectionData(), updated.nextCorner()));
        });
        ctx.setPacketHandled(true);
    }

    private static WandDataNbt.WandData applyAction(ServerPlayer player, WandDataNbt.WandData data,
                                                    WandEditActionPacket msg) {
        return switch (msg.action) {
            case MOVE_SELECTION -> data.withMovedSelection(msg.dx, msg.dy, msg.dz);
            case RESIZE_FACE -> msg.direction == null ? data : data.withResizedFace(msg.direction, msg.dx);
            case MOVE_LABEL -> {
                var existing = data.labels().stream().filter(l -> l.name().equals(msg.primaryText)).findFirst();
                if (existing.isEmpty()) yield data;
                yield data.withMovedLabel(msg.primaryText, existing.get().relativePos().offset(msg.dx, msg.dy, msg.dz));
            }
            case ADD_OR_UPDATE_LABEL -> {
                String cleanName = msg.primaryText.trim();
                if (cleanName.isBlank()) yield data;
                String role = LabelRole.fromId(msg.secondaryText).id();
                yield data.withUpsertedLabel(new WandSelectionData.LabelEntry(cleanName, role, msg.pos, msg.direction));
            }
            case RENAME_LABEL -> data.withRenamedLabel(msg.primaryText, msg.secondaryText);
            case DELETE_LABEL -> data.withRemovedLabel(msg.primaryText);
            case SET_LABEL_ROLE -> {
                var existing = data.labels().stream().filter(l -> l.name().equals(msg.primaryText)).findFirst();
                if (existing.isEmpty()) yield data;
                var l = existing.get();
                yield data.withUpsertedLabel(
                        new WandSelectionData.LabelEntry(l.name(), LabelRole.fromId(msg.secondaryText).id(),
                                l.relativePos(), l.facing()));
            }
            case SET_EXPORT_INFO -> data.withExportInfo(msg.primaryText, msg.secondaryText);
            case EXPORT_NOW -> {
                WandDataNbt.WandData withInfo = data.withExportInfo(msg.primaryText, msg.secondaryText);
                if (!withInfo.hasBounds()) {
                    player.sendSystemMessage(QaWandTexts.CMD_NO_BOUNDS.component().withStyle(ChatFormatting.RED));
                    yield withInfo;
                }
                String name = withInfo.exportName().isBlank() ? "exported_structure" : withInfo.exportName();
                try {
                    ServerLevel level = player.serverLevel();
                    var res = StructureExportService.exportStructure(level, withInfo, name,
                            withInfo.exportNamespace(), StructureExportService.defaultExportRoot());
                    player.sendSystemMessage(QaWandTexts.CMD_EXPORTED.component(
                            res.templateId().toString(), res.blockCount(), res.labelCount(),
                            res.nbtPath().getParent().toString()).withStyle(ChatFormatting.GREEN));
                } catch (IOException e) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Export failed: " +
                            e.getMessage()).withStyle(ChatFormatting.RED));
                }
                yield withInfo;
            }
        };
    }
}
