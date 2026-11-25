package com.typeomega.qa.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.typeomega.qa.spi.WandSelectionData;
import com.typeomega.qa.visual.ClientVisualHooks;
import com.typeomega.qa.wand.WandDataNbt;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C packet synchronizing {@link WandSelectionData} onto the client player's held Omega QA Wand stack.
 */
public record SyncWandStatePacket(
                                  InteractionHand hand,
                                  WandSelectionData selection,
                                  WandDataNbt.NextCorner nextCorner,
                                  boolean openEditor) {

    public SyncWandStatePacket(InteractionHand hand, WandSelectionData selection, WandDataNbt.NextCorner nextCorner) {
        this(hand, selection, nextCorner, false);
    }

    public static void encode(SyncWandStatePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.hand);
        buf.writeBoolean(msg.openEditor);
        writeNullablePos(buf, msg.selection.pos1());
        writeNullablePos(buf, msg.selection.pos2());
        buf.writeEnum(msg.nextCorner);
        buf.writeUtf(msg.selection.exportNamespace(), 128);
        buf.writeUtf(msg.selection.exportName(), 128);
        buf.writeVarInt(msg.selection.labels().size());
        for (WandSelectionData.LabelEntry entry : msg.selection.labels()) {
            buf.writeUtf(entry.name(), 128);
            buf.writeUtf(entry.role(), 64);
            buf.writeBlockPos(entry.relativePos());
            buf.writeBoolean(entry.facing() != null);
            if (entry.facing() != null) buf.writeEnum(entry.facing());
        }
    }

    public static SyncWandStatePacket decode(FriendlyByteBuf buf) {
        InteractionHand hand = buf.readEnum(InteractionHand.class);
        boolean openEditor = buf.readBoolean();
        BlockPos pos1 = readNullablePos(buf);
        BlockPos pos2 = readNullablePos(buf);
        WandDataNbt.NextCorner corner = buf.readEnum(WandDataNbt.NextCorner.class);
        String ns = buf.readUtf(128);
        String name = buf.readUtf(128);
        int count = buf.readVarInt();
        List<WandSelectionData.LabelEntry> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String lName = buf.readUtf(128);
            String role = buf.readUtf(64);
            BlockPos rel = buf.readBlockPos();
            Direction facing = buf.readBoolean() ? buf.readEnum(Direction.class) : null;
            labels.add(new WandSelectionData.LabelEntry(lName, role, rel, facing));
        }
        return new SyncWandStatePacket(hand, new WandSelectionData(pos1, pos2, ns, name, labels), corner, openEditor);
    }

    public static void handle(SyncWandStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(
                () -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisualHooks.applyWandSync(msg)));
        ctx.setPacketHandled(true);
    }

    private static void writeNullablePos(FriendlyByteBuf buf, @Nullable BlockPos pos) {
        buf.writeBoolean(pos != null);
        if (pos != null) buf.writeBlockPos(pos);
    }

    private static @Nullable BlockPos readNullablePos(FriendlyByteBuf buf) {
        return buf.readBoolean() ? buf.readBlockPos() : null;
    }
}
