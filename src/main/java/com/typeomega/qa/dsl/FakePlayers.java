package com.typeomega.qa.dsl;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

/** Unified survival fake-player factory for GameTests (replaces per-mod test player helpers). */
public final class FakePlayers {

    private FakePlayers() {}

    public static ServerPlayer survival(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        return survival(helper, "omegaqa_" + id.toString().substring(0, 8), id);
    }

    public static ServerPlayer survival(GameTestHelper helper, String name) {
        return survival(helper, name, UUID.nameUUIDFromBytes(("omegaqa:" + name).getBytes()));
    }

    private static ServerPlayer survival(GameTestHelper helper, String name, UUID id) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(id, name));
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }
}
