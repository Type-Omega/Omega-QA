package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.log.EventCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Bounded regional simulation runner that ticks GTCEu machines deterministically in {@code (x, y, z)} order without
 * advancing global server time, recording state transitions through {@link WarpDiffer}.
 */
public final class TimeWarpRunner {

    @FunctionalInterface
    public interface VirtualEnergyInjector {

        void inject(MetaMachine machine, long warpTick);
    }

    private final OmegaTestHelper helper;
    private final List<MetaMachine> explicitMachines;
    private final BoundingBox region;
    private final WarpDiffer differ = new WarpDiffer();
    private final List<Runnable> invariants = new ArrayList<>();
    private VirtualEnergyInjector energyInjector;

    private TimeWarpRunner(OmegaTestHelper helper, List<MetaMachine> explicitMachines, BoundingBox region) {
        this.helper = helper;
        this.explicitMachines = explicitMachines;
        this.region = region;
    }

    public static TimeWarpRunner forStructure(PlacedStructure placed) {
        return new TimeWarpRunner(placed.helper(), null, placed.boundingBox());
    }

    public static TimeWarpRunner forBox(OmegaTestHelper helper, BoundingBox box) {
        return new TimeWarpRunner(helper, null, Objects.requireNonNull(box, "box"));
    }

    public static TimeWarpRunner forMachine(OmegaTestHelper helper, MetaMachine machine) {
        return new TimeWarpRunner(helper, List.of(Objects.requireNonNull(machine, "machine")), null);
    }

    public TimeWarpRunner keepEnergyFull() {
        this.energyInjector = (machine, tick) -> {
            if (machine instanceof WorkableTieredMachine wt) {
                wt.energyContainer.setEnergyStored(wt.energyContainer.getEnergyCapacity());
            } else if (machine instanceof MultiblockControllerMachine ctrl) {
                for (IMultiPart part : ctrl.getParts()) {
                    if (part instanceof EnergyHatchPartMachine hatch &&
                            hatch.energyContainer.getHandlerIO() == IO.IN) {
                        hatch.energyContainer.setEnergyStored(hatch.energyContainer.getEnergyCapacity());
                    }
                }
            }
        };
        return this;
    }

    public TimeWarpRunner withEnergyInjector(VirtualEnergyInjector injector) {
        this.energyInjector = injector;
        return this;
    }

    public TimeWarpRunner onWarpTick(Runnable invariant) {
        invariants.add(Objects.requireNonNull(invariant, "invariant"));
        return this;
    }

    public TimeWarpRunner warpTicks(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        List<MetaMachine> machines = collectMachines();
        long baseTick = helper.getTick();
        for (int i = 0; i < ticks; i++) {
            stepOne(baseTick + i + 1, machines);
        }
        helper.log(EventCategory.SEQUENCE, "Time-warped " + ticks + " ticks across " + machines.size() + " machine(s)");
        return this;
    }

    public TimeWarpRunner warpUntilIdle(int maxTicks) {
        return warpUntil(maxTicks, () -> collectMachines().stream()
                .filter(IRecipeLogicMachine.class::isInstance)
                .map(m -> ((IRecipeLogicMachine) m).getRecipeLogic())
                .noneMatch(logic -> logic.isWorking() || logic.isActive()),
                "all machines idle");
    }

    public TimeWarpRunner warpUntil(int maxTicks, BooleanSupplier stopCondition, String desc) {
        if (maxTicks < 1) throw new IllegalArgumentException("maxTicks must be >= 1");
        List<MetaMachine> machines = collectMachines();
        long baseTick = helper.getTick();
        for (int i = 0; i < maxTicks; i++) {
            stepOne(baseTick + i + 1, machines);
            if (stopCondition.getAsBoolean()) {
                helper.log(EventCategory.SEQUENCE, "Time-warp stopped at +" + (i + 1) + "t (" + desc + ")");
                return this;
            }
        }
        throw new AssertionError("Time-warp exceeded " + maxTicks + "t waiting for: " + desc);
    }

    private void stepOne(long tick, List<MetaMachine> machines) {
        for (MetaMachine machine : machines) {
            if (energyInjector != null) {
                energyInjector.inject(machine, tick);
            }
            if (machine instanceof IRecipeLogicMachine rlm) {
                rlm.getRecipeLogic().serverTick();
            }
            differ.sample(tick, machine, helper.events());
        }
        for (Runnable invariant : invariants) {
            invariant.run();
        }
    }

    private List<MetaMachine> collectMachines() {
        List<MetaMachine> out = new ArrayList<>();
        if (explicitMachines != null) {
            out.addAll(explicitMachines);
        } else if (region != null) {
            ServerLevel level = helper.level();
            for (int x = region.minX(); x <= region.maxX(); x++) {
                for (int y = region.minY(); y <= region.maxY(); y++) {
                    for (int z = region.minZ(); z <= region.maxZ(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (level.getBlockEntity(pos) instanceof IMachineBlockEntity be) {
                            out.add(be.getMetaMachine());
                        }
                    }
                }
            }
        }
        out.sort(Comparator.comparingInt((MetaMachine m) -> m.getPos().getX())
                .thenComparingInt(m -> m.getPos().getY())
                .thenComparingInt(m -> m.getPos().getZ()));
        return out;
    }
}
