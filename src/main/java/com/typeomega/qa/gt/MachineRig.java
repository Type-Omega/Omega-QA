package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.log.EventCategory;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Fluent singleblock test rig supporting electric ({@link WorkableTieredMachine} / {@link SimpleTieredMachine}) and
 * steam ({@link SimpleSteamMachine}) machines.
 */
public final class MachineRig {

    private final OmegaTestHelper helper;
    private final MetaMachine machine;
    private final BlockPos absPos;

    private MachineRig(OmegaTestHelper helper, MetaMachine machine, BlockPos absPos) {
        this.helper = helper;
        this.machine = machine;
        this.absPos = absPos;
    }

    public static MachineRig place(GameTestHelper helper, BlockPos relPos, MachineDefinition def) {
        Objects.requireNonNull(def, "def");
        OmegaTestHelper oh = OmegaTestHelper.wrap(helper);
        helper.setBlock(relPos, def.getBlock());
        BlockPos abs = helper.absolutePos(relPos);
        if (!(helper.getBlockEntity(relPos) instanceof IMachineBlockEntity be)) {
            throw new AssertionError("Placed " + def.getId() + " at " + abs + " did not create IMachineBlockEntity");
        }
        MetaMachine meta = be.getMetaMachine();
        oh.log(EventCategory.STRUCTURE, "Placed machine " + def.getId() + " at " + abs);
        return new MachineRig(oh, meta, abs);
    }

    public static MachineRig place(GameTestHelper helper, BlockPos relPos, MachineDefinition[] tieredDefs, int tier) {
        return place(helper, relPos, tieredDefs[tier]);
    }

    public MetaMachine machine() {
        return machine;
    }

    public BlockPos absPos() {
        return absPos;
    }

    public MachineRig circuit(int circuitNumber) {
        ItemStack stack = circuitNumber <= 0 ? ItemStack.EMPTY : IntCircuitBehaviour.stack(circuitNumber);
        if (machine instanceof IHasCircuitSlot slotted) {
            slotted.getCircuitInventory().setStackInSlot(0, stack);
        }
        return this;
    }

    public MachineRig fillEnergy() {
        if (machine instanceof WorkableTieredMachine tiered) {
            tiered.energyContainer.setEnergyStored(tiered.energyContainer.getEnergyCapacity());
            helper.log(EventCategory.ENERGY, "Filled energy on " + machine.getDefinition().getId());
        }
        return this;
    }

    public MachineRig fillSteam(int mb) {
        if (machine instanceof SimpleSteamMachine steam) {
            FluidStack stack = com.gregtechceu.gtceu.common.data.GTMaterials.Steam.getFluid(mb);
            steam.steamTank.fillInternal(stack, IFluidHandler.FluidAction.EXECUTE);
            helper.log(EventCategory.FLUID, "Filled " + mb + "mB steam on " + machine.getDefinition().getId());
        }
        return this;
    }

    public MachineRig insertItem(int slot, ItemStack stack) {
        NotifiableItemStackHandler importItems = importItems();
        importItems.setStackInSlot(slot, stack.copy());
        helper.log(EventCategory.ITEM, "Set slot " + slot + " to " + stack.getCount() + "x " + stack.getItem());
        return this;
    }

    public MachineRig insertFluid(int tank, FluidStack fluid) {
        NotifiableFluidTank importFluids = importFluids();
        importFluids.setFluidInTank(tank, fluid.copy());
        helper.log(EventCategory.FLUID, "Set tank " + tank + " to " + fluid.getAmount() + "mB " + fluid.getFluid());
        return this;
    }

    public int outputItemCount(Item item) {
        return outputItemCount(stack -> stack.is(item));
    }

    public int outputItemCount(Predicate<ItemStack> predicate) {
        NotifiableItemStackHandler out = exportItems();
        int total = 0;
        for (int i = 0; i < out.getSlots(); i++) {
            ItemStack stack = out.getStackInSlot(i);
            if (!stack.isEmpty() && predicate.test(stack)) total += stack.getCount();
        }
        return total;
    }

    public int outputFluidAmount(Material material) {
        return outputFluidAmount(material.getFluid(1));
    }

    public int outputFluidAmount(FluidStack wanted) {
        NotifiableFluidTank out = exportFluids();
        int total = 0;
        for (int i = 0; i < out.getTanks(); i++) {
            FluidStack fluid = out.getFluidInTank(i);
            if (fluid.isFluidEqual(wanted)) total += fluid.getAmount();
        }
        return total;
    }

    public RecipeLogic recipeLogic() {
        if (machine instanceof IRecipeLogicMachine rlm) return rlm.getRecipeLogic();
        throw new AssertionError("Machine " + machine.getDefinition().getId() + " has no RecipeLogic");
    }

    public MachineRig findRecipe() {
        recipeLogic().findAndHandleRecipe();
        return this;
    }

    public MachineRig assertRunningRecipe(ResourceLocation recipeId) {
        RecipeLogic logic = recipeLogic();
        if (logic.getLastRecipe() == null) logic.findAndHandleRecipe();
        var recipe = logic.getLastRecipe();
        if (recipe == null || !recipeId.equals(recipe.getId())) {
            throw new AssertionError("Expected running recipe " + recipeId + " on " +
                    machine.getDefinition().getId() + ", got " + (recipe == null ? "none" : recipe.getId()));
        }
        return this;
    }

    public MachineRig finishCurrentRecipe() {
        RecipeLogic logic = recipeLogic();
        if (logic.getLastRecipe() == null) logic.findAndHandleRecipe();
        logic.onRecipeFinish();
        return this;
    }

    public TimeWarpRunner timeWarp() {
        return TimeWarpRunner.forMachine(helper, machine);
    }

    private NotifiableItemStackHandler importItems() {
        if (machine instanceof WorkableTieredMachine wt) return wt.importItems;
        if (machine instanceof SimpleSteamMachine ss) return ss.importItems;
        throw new AssertionError("Machine has no importItems: " + machine.getDefinition().getId());
    }

    private NotifiableItemStackHandler exportItems() {
        if (machine instanceof WorkableTieredMachine wt) return wt.exportItems;
        if (machine instanceof SimpleSteamMachine ss) return ss.exportItems;
        throw new AssertionError("Machine has no exportItems: " + machine.getDefinition().getId());
    }

    private NotifiableFluidTank importFluids() {
        if (machine instanceof WorkableTieredMachine wt) return wt.importFluids;
        throw new AssertionError("Machine has no importFluids: " + machine.getDefinition().getId());
    }

    private NotifiableFluidTank exportFluids() {
        if (machine instanceof WorkableTieredMachine wt) return wt.exportFluids;
        throw new AssertionError("Machine has no exportFluids: " + machine.getDefinition().getId());
    }
}
