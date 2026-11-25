package com.typeomega.qa.compat.ftb;

import net.minecraft.gametest.framework.GameTestHelper;

import com.typeomega.qa.dsl.OmegaTestHelper;

import java.util.UUID;

/**
 * {@code ModList}-guarded helper for creating temporary FTB Teams parties in GameTests and tearing them down
 * automatically via {@code afterTest}.
 */
public final class TeamTestHelper {

    private TeamTestHelper() {}

    public static UUID createTemporaryParty(GameTestHelper helper, UUID... memberUuids) {
        OmegaTestHelper oh = OmegaTestHelper.wrap(helper);
        oh.assumeModLoaded("ftbteams");
        UUID partyId = UUID.randomUUID();
        AutoCloseable handle = FtbTeamBridge.bindParty(partyId, memberUuids);
        oh.afterTest(() -> {
            try {
                handle.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return partyId;
    }

    public static UUID resolveTeamOrPlayer(GameTestHelper helper, UUID playerUuid) {
        OmegaTestHelper oh = OmegaTestHelper.wrap(helper);
        oh.assumeModLoaded("ftbteams");
        return FtbTeamBridge.resolveTeamId(playerUuid);
    }
}
