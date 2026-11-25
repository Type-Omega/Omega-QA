package com.typeomega.qa.compat.ftb;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.data.PartyTeam;
import dev.ftb.mods.ftbteams.data.PlayerTeam;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** FTB Teams implementation bridge isolated behind {@link TeamTestHelper}. */
final class FtbTeamBridge {

    private FtbTeamBridge() {}

    @SuppressWarnings("unchecked")
    static AutoCloseable bindParty(UUID partyId, UUID... memberUuids) {
        TeamManagerImpl manager = (TeamManagerImpl) FTBTeamsAPI.api().getManager();
        try {
            Field knownField = TeamManagerImpl.class.getDeclaredField("knownPlayers");
            knownField.setAccessible(true);
            Map<UUID, Team> known = (Map<UUID, Team>) knownField.get(manager);
            PartyTeam party = new PartyTeam(manager, partyId);
            List<UUID> added = new ArrayList<>();
            for (UUID member : memberUuids) {
                PlayerTeam playerTeam = new PlayerTeam(manager, member);
                playerTeam.setEffectiveTeam(party);
                known.put(member, playerTeam);
                added.add(member);
            }
            return () -> {
                for (UUID member : added) known.remove(member);
            };
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create temporary FTB Teams party", e);
        }
    }

    static UUID resolveTeamId(UUID playerUuid) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerUuid)
                .map(Team::getId)
                .orElse(playerUuid);
    }
}
