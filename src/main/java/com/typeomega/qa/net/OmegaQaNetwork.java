package com.typeomega.qa.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.typeomega.qa.OmegaQA;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Forge {@link SimpleChannel} for Omega-QA wand state synchronization, editor actions, live failure overlays,
 * and report screen delivery.
 */
public final class OmegaQaNetwork {

    private static final String PROTOCOL = "1";
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.tryBuild(OmegaQA.MOD_ID, "omegaqa"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private OmegaQaNetwork() {}

    public static void init() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        int id = 0;
        CHANNEL.registerMessage(id++, SyncWandStatePacket.class,
                SyncWandStatePacket::encode, SyncWandStatePacket::decode, SyncWandStatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, WandEditActionPacket.class,
                WandEditActionPacket::encode, WandEditActionPacket::decode, WandEditActionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, FailureOverlaySyncPacket.class,
                FailureOverlaySyncPacket::encode, FailureOverlaySyncPacket::decode, FailureOverlaySyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ReportSyncPacket.class,
                ReportSyncPacket::encode, ReportSyncPacket::decode, ReportSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (player != null && player.connection != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void broadcastFailureSync(FailureOverlaySyncPacket packet) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && !server.getPlayerList().getPlayers().isEmpty()) {
            CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        }
    }
}
