package com.typeomega.qa.net;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.visual.ClientVisualHooks;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * S2C packet replicating {@link FailureDiagnostic} events to client overlays ({@code DebugBeaconRenderer},
 * {@code GhostBlockDiffRenderer}, {@code FloatingTextRenderer}).
 */
public record FailureOverlaySyncPacket(
                                       Mode mode,
                                       @Nullable FailureDiagnostic diagnostic,
                                       String clearTestId) {

    public enum Mode {
        PUBLISH,
        CLEAR_TEST,
        CLEAR_ALL
    }

    public static FailureOverlaySyncPacket publish(FailureDiagnostic diagnostic) {
        return new FailureOverlaySyncPacket(Mode.PUBLISH, diagnostic, "");
    }

    public static FailureOverlaySyncPacket clearTest(String testId) {
        return new FailureOverlaySyncPacket(Mode.CLEAR_TEST, null, testId == null ? "" : testId);
    }

    public static FailureOverlaySyncPacket clearAll() {
        return new FailureOverlaySyncPacket(Mode.CLEAR_ALL, null, "");
    }

    public static void encode(FailureOverlaySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.mode);
        if (msg.mode == Mode.PUBLISH && msg.diagnostic != null) {
            FailureDiagnostic d = msg.diagnostic;
            buf.writeUtf(d.testId(), 256);
            buf.writeBlockPos(d.cellOrigin());
            buf.writeVarInt(d.cellSize().getX());
            buf.writeVarInt(d.cellSize().getY());
            buf.writeVarInt(d.cellSize().getZ());
            buf.writeBoolean(d.failPos() != null);
            if (d.failPos() != null) buf.writeBlockPos(d.failPos());
            writeState(buf, d.expectedState());
            writeState(buf, d.actualState());
            buf.writeUtf(d.message() == null ? "" : d.message(), 1024);
            buf.writeEnum(d.status());
        } else if (msg.mode == Mode.CLEAR_TEST) {
            buf.writeUtf(msg.clearTestId, 256);
        }
    }

    public static FailureOverlaySyncPacket decode(FriendlyByteBuf buf) {
        Mode mode = buf.readEnum(Mode.class);
        if (mode == Mode.PUBLISH) {
            String testId = buf.readUtf(256);
            BlockPos origin = buf.readBlockPos();
            Vec3i size = new Vec3i(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            BlockPos failPos = buf.readBoolean() ? buf.readBlockPos() : null;
            BlockState expected = readState(buf);
            BlockState actual = readState(buf);
            String message = buf.readUtf(1024);
            FailureDiagnostic.TestStatus status = buf.readEnum(FailureDiagnostic.TestStatus.class);
            return new FailureOverlaySyncPacket(mode,
                    new FailureDiagnostic(testId, origin, size, failPos, expected, actual, message, status), "");
        }
        if (mode == Mode.CLEAR_TEST) {
            return new FailureOverlaySyncPacket(mode, null, buf.readUtf(256));
        }
        return clearAll();
    }

    public static void handle(FailureOverlaySyncPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            switch (msg.mode) {
                case PUBLISH -> {
                    if (msg.diagnostic != null) ClientVisualHooks.applyLocalDiagnostic(msg.diagnostic);
                }
                case CLEAR_TEST -> ClientVisualHooks.clearLocalTest(msg.clearTestId);
                case CLEAR_ALL -> ClientVisualHooks.clearAllLocal();
            }
        }));
        ctx.setPacketHandled(true);
    }

    private static void writeState(FriendlyByteBuf buf, @Nullable BlockState state) {
        buf.writeBoolean(state != null);
        if (state != null) buf.writeVarInt(Block.getId(state));
    }

    private static @Nullable BlockState readState(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) return null;
        return Block.stateById(buf.readVarInt());
    }
}
