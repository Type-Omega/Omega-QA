package com.typeomega.qa.worldgen;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Deterministic GTCEu ore vein and chunk probe utility for GameTests covering {@code GTRegistries.ORE_VEINS}
 * registration, dimension/layer/material invariants, and physical chunk ore/indicator scanning.
 */
public final class WorldgenProbeRig {

    public record ChunkScanSummary(int chunksScanned, int matchingOreBlocks, int matchingIndicators,
                                   int invalidIndicatorsWithoutAirAbove) {}

    private WorldgenProbeRig() {}

    public static GTOreDefinition requireVein(ResourceLocation veinId) {
        GTOreDefinition def = GTRegistries.ORE_VEINS.get(Objects.requireNonNull(veinId, "veinId"));
        if (def == null) {
            throw new AssertionError("Expected registered GTOreDefinition: " + veinId);
        }
        return def;
    }

    public static List<Material> veinMaterials(ResourceLocation veinId) {
        GTOreDefinition def = requireVein(veinId);
        if (def.veinGenerator() == null) return List.of();
        return def.veinGenerator().getAllMaterials();
    }

    public static void assertVeinContains(ResourceLocation veinId, Material... expected) {
        List<Material> mats = veinMaterials(veinId);
        for (Material m : expected) {
            if (!mats.contains(m)) {
                throw new AssertionError("Vein " + veinId + " missing material " + m.getResourceLocation() +
                        ", found " + mats);
            }
        }
    }

    public static void assertVeinExcludes(ResourceLocation veinId, Material... forbidden) {
        List<Material> mats = veinMaterials(veinId);
        for (Material m : forbidden) {
            if (mats.contains(m)) {
                throw new AssertionError("Vein " + veinId + " unexpectedly contains " + m.getResourceLocation());
            }
        }
    }

    public static void assertVeinDimension(ResourceLocation veinId, ResourceKey<Level> dimension) {
        GTOreDefinition def = requireVein(veinId);
        Set<ResourceKey<Level>> filter = def.dimensionFilter();
        boolean inFilter = filter != null && filter.contains(dimension);
        boolean inLayer = def.layer() != null && def.layer().isApplicableForLevel(dimension.location());
        if (!inFilter && !inLayer) {
            throw new AssertionError("Vein " + veinId + " is not applicable to dimension " + dimension.location());
        }
    }

    /**
     * Scans a deterministic square grid of chunks around {@code centerChunk} in {@code level}, counting matching ore
     * blocks and surface indicator blocks (verifying indicators have air directly above them).
     */
    public static ChunkScanSummary scanChunks(GameTestHelper helper, ChunkPos centerChunk, int radiusChunks,
                                              int minY, int maxY, Predicate<BlockState> oreMatcher,
                                              Predicate<BlockState> indicatorMatcher) {
        ServerLevel level = helper.getLevel();
        int chunks = 0;
        int ores = 0;
        int indicators = 0;
        int invalidIndicators = 0;
        int loY = Math.max(level.getMinBuildHeight(), minY);
        int hiY = Math.min(level.getMaxBuildHeight() - 1, maxY);

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                LevelChunk chunk = level.getChunk(centerChunk.x + dx, centerChunk.z + dz);
                chunks++;
                int baseX = chunk.getPos().getMinBlockX();
                int baseZ = chunk.getPos().getMinBlockZ();
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = loY; y <= hiY; y++) {
                            cursor.set(baseX + x, y, baseZ + z);
                            BlockState state = chunk.getBlockState(cursor);
                            if (state.isAir()) continue;
                            if (oreMatcher.test(state)) ores++;
                            if (indicatorMatcher.test(state)) {
                                indicators++;
                                cursor.setY(y + 1);
                                if (!chunk.getBlockState(cursor).isAir()) {
                                    invalidIndicators++;
                                }
                            }
                        }
                    }
                }
            }
        }
        return new ChunkScanSummary(chunks, ores, indicators, invalidIndicators);
    }

    public static Predicate<BlockState> blockIdContains(String token) {
        return state -> {
            Block b = state.getBlock();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
            return id != null && id.toString().contains(token);
        };
    }
}
