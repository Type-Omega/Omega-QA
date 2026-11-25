package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.SpecialHatchInspector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Fluent facade over {@link MultiblockControllerMachine} providing native {@code PatternError} diagnostics,
 * {@code assertNeverForms}, maintenance/tier helpers, live hatch inspection (incl. {@link SpecialHatchInspector}),
 * and deterministic EU/item/fluid injection and drainage.
 */
public final class MultiblockRig {

    private final OmegaTestHelper helper;
    private final MultiblockControllerMachine controller;
    private final @Nullable PlacedStructure structure;

    private MultiblockRig(OmegaTestHelper helper, MultiblockControllerMachine controller,
                          @Nullable PlacedStructure structure) {
        this.helper = Objects.requireNonNull(helper, "helper");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.structure = structure;
    }

    public static MultiblockRig of(GameTestHelper helper, MultiblockControllerMachine controller) {
        return of(OmegaTestHelper.wrap(helper), controller, null);
    }

    public static MultiblockRig of(OmegaTestHelper helper, MultiblockControllerMachine controller,
                                   @Nullable PlacedStructure structure) {
        return new MultiblockRig(helper, controller, structure);
    }

    public static MultiblockRig at(GameTestHelper helper, BlockPos relPos) {
        OmegaTestHelper oh = OmegaTestHelper.wrap(helper);
        BlockPos abs = helper.absolutePos(relPos);
        if (helper.getLevel().getBlockEntity(abs) instanceof IMachineBlockEntity be &&
                be.getMetaMachine() instanceof MultiblockControllerMachine ctrl) {
            return new MultiblockRig(oh, ctrl, null);
        }
        throw new AssertionError("Expected MultiblockControllerMachine at " + abs);
    }

    public MultiblockControllerMachine controller() {
        return controller;
    }

    public @Nullable PlacedStructure structure() {
        return structure;
    }

    public MultiblockRig assertFormed() {
        boolean ok = controller.checkPatternWithLock();
        if (ok) {
            controller.onStructureFormed();
        }
        if (!ok || !controller.isFormed()) {
            PatternError error = controller.getMultiblockState().error;
            BlockPos failPos = error != null ? error.getPos() : controller.getPos();
            BlockState actual = failPos != null ? helper.level().getBlockState(failPos) : null;
            BlockState expected = extractExpectedState(error);
            String msg = "Multiblock " + controller.getDefinition().getId() + " failed to form: " +
                    (error == null ? "no PatternError reported" :
                            error.getErrorInfo().getString() + " at " + failPos + " (found " + actual + ")");
            helper.log(EventCategory.STRUCTURE, msg);
            BlockPos origin = structure != null ? structure.origin() : helper.session().cellOrigin();
            Vec3i size = structure != null ? structure.size() : helper.session().cellSize();
            OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                    helper.session().testId(), origin, size, failPos, expected, actual, msg,
                    FailureDiagnostic.TestStatus.FAILED));
            throw new AssertionError(msg);
        }
        helper.log(EventCategory.STRUCTURE,
                "Formed " + controller.getDefinition().getId() + " at " + controller.getPos());
        return this;
    }

    public MultiblockRig assertNotFormed() {
        controller.checkPatternWithLock();
        if (controller.isFormed()) {
            String msg = "Expected " + controller.getDefinition().getId() + " NOT to form at " + controller.getPos();
            helper.log(EventCategory.STRUCTURE, msg);
            throw new AssertionError(msg);
        }
        return this;
    }

    /** Forces an initial check, asserts unformed on every tick for {@code observationTicks}, then succeeds. */
    public void assertNeverForms(int observationTicks, String invariantName) {
        assertNotFormed();
        helper.onEachTick(invariantName, () -> {
            if (controller.isFormed()) {
                throw new AssertionError("Controller " + controller.getDefinition().getId() +
                        " unexpectedly formed at tick " + helper.getTick());
            }
        });
        helper.succeedAtTimeout(observationTicks);
    }

    public MultiblockRig fixMaintenance() {
        // EN: Preview maintenance hatches can spawn with all 6 problems; clear them so recipe tests are deterministic.
        // PT: Hatches de manutenção de preview podem nascer com os 6 problemas; limpamos para determinismo.
        // ZH: 预览生成的维护仓可能带有全部 6 个问题；在测试中将其清除以保证配方测试确定性。
        for (IMultiPart part : parts()) {
            if (part instanceof IMaintenanceMachine maint) {
                maint.setMaintenanceProblems(IMaintenanceMachine.NO_PROBLEMS);
            }
        }
        return this;
    }

    public MultiblockRig assertMaintenanceClean() {
        for (IMultiPart part : parts()) {
            if (part instanceof IMaintenanceMachine maint && maint.hasMaintenanceProblems()) {
                throw new AssertionError("Maintenance hatch at " + part.self().getPos() + " has " +
                        maint.getNumMaintenanceProblems() + " problem(s)");
            }
        }
        return this;
    }

    public MultiblockRig assertTier(int expectedTier) {
        int actual = controller instanceof ITieredMachine tiered ? tiered.getTier() : -1;
        if (actual != expectedTier) {
            throw new AssertionError("Expected tier " + expectedTier + " for " + controller.getDefinition().getId() +
                    " but got " + actual);
        }
        return this;
    }

    public List<IMultiPart> parts() {
        return controller.getParts();
    }

    public List<ItemBusPartMachine> inputBusses() {
        return partsOf(ItemBusPartMachine.class, b -> b.getInventory().getHandlerIO() == IO.IN);
    }

    public List<ItemBusPartMachine> outputBusses() {
        return partsOf(ItemBusPartMachine.class, b -> b.getInventory().getHandlerIO() == IO.OUT);
    }

    public List<FluidHatchPartMachine> inputHatches() {
        return partsOf(FluidHatchPartMachine.class, h -> h.tank.getHandlerIO() == IO.IN);
    }

    public List<FluidHatchPartMachine> outputHatches() {
        return partsOf(FluidHatchPartMachine.class, h -> h.tank.getHandlerIO() == IO.OUT);
    }

    public List<EnergyHatchPartMachine> energyHatches() {
        return partsOf(EnergyHatchPartMachine.class, e -> e.energyContainer.getHandlerIO() == IO.IN);
    }

    public List<EnergyHatchPartMachine> dynamoHatches() {
        return partsOf(EnergyHatchPartMachine.class, e -> e.energyContainer.getHandlerIO() == IO.OUT);
    }

    public int totalParallelLimit() {
        int total = 1;
        for (IMultiPart part : parts()) {
            if (part instanceof MetaMachine meta) {
                for (SpecialHatchInspector inspector : OmegaQaBootstrap.specialHatchInspectors()) {
                    OptionalInt limit = inspector.parallelLimit(meta);
                    if (limit.isPresent()) total = Math.max(total, limit.getAsInt());
                }
            }
        }
        return total;
    }

    public MultiblockRig fillEnergyHatches() {
        for (EnergyHatchPartMachine hatch : energyHatches()) {
            hatch.energyContainer.setEnergyStored(hatch.energyContainer.getEnergyCapacity());
        }
        helper.log(EventCategory.ENERGY, "Filled energy hatches on " + controller.getDefinition().getId());
        return this;
    }

    public MultiblockRig supplyEnergy(long euPerTick, int ticks) {
        long remaining = euPerTick * ticks;
        for (EnergyHatchPartMachine hatch : energyHatches()) {
            if (remaining <= 0) break;
            long added = hatch.energyContainer.changeEnergy(remaining);
            remaining -= added;
        }
        return this;
    }

    public long readDynamoEnergy() {
        long total = 0L;
        for (EnergyHatchPartMachine hatch : dynamoHatches()) {
            total += hatch.energyContainer.getEnergyStored();
        }
        return total;
    }

    public long drainEnergy() {
        long drained = 0L;
        for (EnergyHatchPartMachine hatch : dynamoHatches()) {
            drained += hatch.energyContainer.getEnergyStored();
            hatch.energyContainer.setEnergyStored(0L);
        }
        return drained;
    }

    public MultiblockRig insertItems(ItemStack... stacks) {
        for (ItemStack stack : stacks) insertItem(stack);
        return this;
    }

    public MultiblockRig insertItem(ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (ItemBusPartMachine bus : inputBusses()) {
            var inv = bus.getInventory();
            for (int slot = 0; slot < inv.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = inv.insertItemInternal(slot, remaining, false);
            }
        }
        if (!remaining.isEmpty()) {
            throw new AssertionError("No room in input busses for " + remaining);
        }
        helper.log(EventCategory.ITEM, "Inserted " + stack.getCount() + "x " + stack.getItem());
        return this;
    }

    public MultiblockRig insertFluid(FluidStack fluid) {
        FluidStack remaining = fluid.copy();
        for (FluidHatchPartMachine hatch : inputHatches()) {
            if (remaining.isEmpty()) break;
            int filled = hatch.tank.fillInternal(remaining, IFluidHandler.FluidAction.EXECUTE);
            remaining.shrink(filled);
        }
        if (!remaining.isEmpty()) {
            throw new AssertionError("No room in input hatches for " + remaining.getAmount() + "mB " +
                    remaining.getFluid());
        }
        helper.log(EventCategory.FLUID, "Inserted " + fluid.getAmount() + "mB " + fluid.getFluid());
        return this;
    }

    public MultiblockRig setCircuit(int circuitNumber) {
        ItemStack circuit = circuitNumber <= 0 ? ItemStack.EMPTY : IntCircuitBehaviour.stack(circuitNumber);
        for (ItemBusPartMachine bus : inputBusses()) {
            bus.getCircuitInventory().setStackInSlot(0, circuit.copy());
        }
        for (FluidHatchPartMachine hatch : inputHatches()) {
            hatch.getCircuitInventory().setStackInSlot(0, circuit.copy());
        }
        return this;
    }

    public MultiblockRig assertOutputItem(Predicate<ItemStack> matcher, int minCount) {
        int found = 0;
        for (ItemBusPartMachine bus : outputBusses()) {
            var inv = bus.getInventory();
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (!stack.isEmpty() && matcher.test(stack)) {
                    found += stack.getCount();
                }
            }
        }
        if (found < minCount) {
            throw new AssertionError("Expected at least " + minCount + " matching output items, found " + found);
        }
        return this;
    }

    public MultiblockRig assertOutputFluid(FluidStack expectedMin) {
        int found = 0;
        for (FluidHatchPartMachine hatch : outputHatches()) {
            for (int t = 0; t < hatch.tank.getTanks(); t++) {
                FluidStack stack = hatch.tank.getFluidInTank(t);
                if (stack.isFluidEqual(expectedMin)) {
                    found += stack.getAmount();
                }
            }
        }
        if (found < expectedMin.getAmount()) {
            throw new AssertionError("Expected at least " + expectedMin.getAmount() + "mB " +
                    expectedMin.getFluid() + ", found " + found + "mB");
        }
        return this;
    }

    public MultiblockRig clearOutputs() {
        for (ItemBusPartMachine bus : outputBusses()) {
            var inv = bus.getInventory();
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                inv.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        for (FluidHatchPartMachine hatch : outputHatches()) {
            for (int t = 0; t < hatch.tank.getTanks(); t++) {
                hatch.tank.setFluidInTank(t, FluidStack.EMPTY);
            }
        }
        return this;
    }

    public TimeWarpRunner timeWarp() {
        if (structure != null) {
            return TimeWarpRunner.forStructure(structure);
        }
        return TimeWarpRunner.forMachine(helper, controller);
    }

    public @Nullable WorkableElectricMultiblockMachine workable() {
        return controller instanceof WorkableElectricMultiblockMachine w ? w : null;
    }

    private <T extends IMultiPart> List<T> partsOf(Class<T> type, Predicate<T> filter) {
        List<T> result = new ArrayList<>();
        for (IMultiPart part : parts()) {
            if (type.isInstance(part)) {
                T cast = type.cast(part);
                if (filter.test(cast)) result.add(cast);
            }
        }
        return result;
    }

    private static @Nullable BlockState extractExpectedState(@Nullable PatternError error) {
        if (error == null) return null;
        try {
            List<List<ItemStack>> candidates = error.getCandidates();
            if (candidates != null) {
                for (List<ItemStack> group : candidates) {
                    if (group == null) continue;
                    for (ItemStack stack : group) {
                        if (!stack.isEmpty() && stack.getItem() instanceof BlockItem blockItem) {
                            return blockItem.getBlock().defaultBlockState();
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {}
        return null;
    }
}
