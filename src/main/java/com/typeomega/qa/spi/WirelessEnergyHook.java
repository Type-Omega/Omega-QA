package com.typeomega.qa.spi;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/** SPI for querying and mutating team/owner wireless EU (and optional steam) without depending on GTNA. */
public interface WirelessEnergyHook {

    long storedEu(ServerLevel level, UUID teamOrOwner);

    void addEu(ServerLevel level, UUID teamOrOwner, long delta);

    default long storedSteam(ServerLevel level, UUID teamOrOwner) {
        return 0L;
    }

    default void addSteam(ServerLevel level, UUID teamOrOwner, long delta) {}
}
