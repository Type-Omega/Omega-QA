package com.typeomega.qa.perf;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.registries.ForgeRegistries;

import com.typeomega.qa.spi.PerfBaselineRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks per-{@code BlockEntityType} / {@code MachineDefinition} tick duration in microseconds and surfaces top
 * offenders for {@link PerfBenchRig}.
 */
public final class BlockEntitySpikeProfiler {

    private static final class Bucket {

        long totalNanos;
        long maxNanos;
        long samples;
    }

    private final Map<String, Bucket> buckets = new HashMap<>();
    private int ticksRecorded;

    public void recordSample(String id, long nanos) {
        if (id == null || id.isBlank()) return;
        Bucket bucket = buckets.computeIfAbsent(id, k -> new Bucket());
        bucket.totalNanos += Math.max(0L, nanos);
        bucket.samples++;
        if (nanos > bucket.maxNanos) bucket.maxNanos = nanos;
    }

    public void markTickComplete() {
        ticksRecorded++;
    }

    /** Probes active machine/block-entity ticks inside {@code box} and records per-type timings. */
    public void profileRegionTick(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    cursor.set(x, y, z);
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be == null) continue;
                    String id = identify(be);
                    long t0 = System.nanoTime();
                    if (be instanceof IMachineBlockEntity mbe) {
                        MetaMachine meta = mbe.getMetaMachine();
                        if (meta instanceof IRecipeLogicMachine rlm) {
                            rlm.getRecipeLogic().serverTick();
                        }
                    }
                    long dt = System.nanoTime() - t0;
                    recordSample(id, dt);
                }
            }
        }
        markTickComplete();
    }

    public List<PerfBaselineRecord.SpikeEntry> topOffenders(int limit) {
        int denom = Math.max(1, ticksRecorded);
        List<PerfBaselineRecord.SpikeEntry> entries = new ArrayList<>(buckets.size());
        for (var e : buckets.entrySet()) {
            Bucket b = e.getValue();
            double avgMicrosPerTick = (b.totalNanos / 1_000.0) / denom;
            double maxMicros = b.maxNanos / 1_000.0;
            entries.add(new PerfBaselineRecord.SpikeEntry(e.getKey(), avgMicrosPerTick, maxMicros));
        }
        entries.sort(Comparator.comparingDouble(PerfBaselineRecord.SpikeEntry::avgMicrosPerTick).reversed());
        return entries.size() <= limit ? List.copyOf(entries) : List.copyOf(entries.subList(0, limit));
    }

    public static String identify(BlockEntity be) {
        if (be instanceof IMachineBlockEntity mbe) {
            return mbe.getMetaMachine().getDefinition().getId().toString();
        }
        ResourceLocation key = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(be.getType());
        return key != null ? key.toString() : be.getClass().getSimpleName();
    }
}
