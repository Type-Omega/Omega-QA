package com.typeomega.qa.compat.ae2;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.pathing.IPathingService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternContainer;
import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.log.EventCategory;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;

/**
 * AE2 implementation bridge isolated from {@link MeNetworkHelper} so loading {@code MeNetworkHelper} never triggers
 * AE2 classloading when AE2 is absent.
 */
final class Ae2GridBridge {

    private final OmegaTestHelper helper;
    private final IGrid grid;

    private Ae2GridBridge(OmegaTestHelper helper, IGrid grid) {
        this.helper = helper;
        this.grid = grid;
    }

    static Ae2GridBridge resolve(OmegaTestHelper helper, BlockPos absPos) {
        BlockEntity be = helper.level().getBlockEntity(absPos);
        IGridNode node = null;
        if (be instanceof IInWorldGridNodeHost host) {
            for (Direction dir : Direction.values()) {
                node = host.getGridNode(dir);
                if (node != null) break;
            }
            if (node == null) node = host.getGridNode(null);
        }
        if (node == null && be instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine() instanceof MEBusPartMachine meBus) {
            IGrid g = meBus.getMainNode().getGrid();
            if (g != null) return new Ae2GridBridge(helper, g);
        }
        if (node == null || node.getGrid() == null) {
            throw new AssertionError("No active AE2 IGrid found at " + absPos);
        }
        return new Ae2GridBridge(helper, node.getGrid());
    }

    void assertOnline() {
        IEnergyService energy = grid.getEnergyService();
        IPathingService pathing = grid.getPathingService();
        if (!energy.isNetworkPowered()) {
            throw new AssertionError("AE2 network is not powered (stored=" + energy.getStoredPower() + ")");
        }
        if (pathing.isNetworkBooting()) {
            throw new AssertionError("AE2 network is still booting");
        }
        helper.log(EventCategory.AE2, "AE2 grid online, nodes=" + grid.size() +
                ", usedChannels=" + pathing.getUsedChannels());
    }

    void assertChannelsUsed(int min, int max) {
        int used = grid.getPathingService().getUsedChannels();
        if (used < min || used > max) {
            throw new AssertionError("Expected AE2 used channels in [" + min + ", " + max + "], got " + used);
        }
    }

    void assertPowerStored(double minAe) {
        double stored = grid.getEnergyService().getStoredPower();
        if (stored < minAe) {
            throw new AssertionError("Expected at least " + minAe + " AE stored, got " + stored);
        }
    }

    long insertItem(ItemStack stack) {
        if (stack.isEmpty()) return 0L;
        IStorageService storage = grid.getStorageService();
        long inserted = storage.getInventory().insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE,
                IActionSource.empty());
        helper.log(EventCategory.AE2, "Inserted " + inserted + "x " + stack.getItem() + " into ME");
        return inserted;
    }

    long insertFluid(FluidStack stack) {
        if (stack.isEmpty()) return 0L;
        IStorageService storage = grid.getStorageService();
        long inserted = storage.getInventory().insert(AEFluidKey.of(stack), stack.getAmount(), Actionable.MODULATE,
                IActionSource.empty());
        helper.log(EventCategory.AE2, "Inserted " + inserted + "mB " + stack.getFluid() + " into ME");
        return inserted;
    }

    void assertStored(Item item, long minAmount) {
        long stored = grid.getStorageService().getCachedInventory().get(AEItemKey.of(item));
        if (stored < minAmount) {
            throw new AssertionError("Expected at least " + minAmount + "x " + item + " in ME, found " + stored);
        }
    }

    void assertStoredFluid(FluidStack fluid) {
        long stored = grid.getStorageService().getCachedInventory().get(AEFluidKey.of(fluid));
        if (stored < fluid.getAmount()) {
            throw new AssertionError("Expected at least " + fluid.getAmount() + "mB " + fluid.getFluid() +
                    " in ME, found " + stored);
        }
    }

    void encodePattern(BlockPos absProviderPos, ItemStack[] inputs, ItemStack[] outputs) {
        GenericStack[] inStacks = new GenericStack[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            inStacks[i] = GenericStack.fromItemStack(inputs[i]);
        }
        GenericStack[] outStacks = new GenericStack[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            outStacks[i] = GenericStack.fromItemStack(outputs[i]);
        }
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(inStacks, outStacks);
        BlockEntity be = helper.level().getBlockEntity(absProviderPos);
        if (be instanceof PatternContainer container) {
            ItemStack leftover = container.getTerminalPatternInventory().addItems(encoded);
            if (!leftover.isEmpty()) {
                throw new AssertionError("PatternContainer at " + absProviderPos + " had no free pattern slot");
            }
        } else if (be instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine() instanceof MEPatternBufferPartMachine buffer) {
                    var inv = buffer.getPatternInventory();
                    boolean placed = false;
                    for (int i = 0; i < inv.getSlots(); i++) {
                        if (inv.getStackInSlot(i).isEmpty()) {
                            inv.setStackInSlot(i, encoded);
                            placed = true;
                            break;
                        }
                    }
                    if (!placed) {
                        throw new AssertionError(
                                "MEPatternBufferPartMachine at " + absProviderPos + " has no free slot");
                    }
                } else {
                    throw new AssertionError("No AE2 PatternContainer or GTCEu MEPatternBuffer at " + absProviderPos);
                }
        helper.log(EventCategory.AE2, "Encoded pattern into " + absProviderPos);
    }

    void requestCraft(ItemStack target, int timeoutTicks) {
        ICraftingService crafting = grid.getCraftingService();
        AEItemKey key = AEItemKey.of(target);
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {

            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public @Nullable IGridNode getGridNode() {
                return grid.getPivot();
            }
        };
        long baseline = grid.getStorageService().getCachedInventory().get(key);
        long wantedTotal = baseline + target.getCount();
        Future<ICraftingPlan> planFuture = crafting.beginCraftingCalculation(helper.level(), requester, key,
                target.getCount(), CalculationStrategy.REPORT_MISSING_ITEMS);

        helper.startSequence()
                .thenWaitUntil(Math.max(20, timeoutTicks / 2), "ae2_craft_plan", () -> {
                    if (!planFuture.isDone()) {
                        throw new AssertionError("AE2 crafting plan calculation not finished");
                    }
                    try {
                        ICraftingPlan plan = planFuture.get();
                        if (!plan.missingItems().isEmpty()) {
                            throw new AssertionError("AE2 crafting missing items: " + plan.missingItems());
                        }
                        ICraftingSubmitResult submit = crafting.submitJob(plan, null, null, true,
                                IActionSource.empty());
                        if (!submit.successful()) {
                            throw new AssertionError("AE2 submitJob failed: " + submit.errorCode() + " (" +
                                    submit.errorDetail() + ")");
                        }
                    } catch (AssertionError ae) {
                        throw ae;
                    } catch (Exception e) {
                        throw new IllegalStateException("AE2 crafting calculation threw", e);
                    }
                })
                .thenWaitUntil(Math.max(20, timeoutTicks), "ae2_craft_complete", () -> {
                    long current = grid.getStorageService().getCachedInventory().get(key);
                    if (current < wantedTotal) {
                        throw new AssertionError("Waiting for ME craft output " + target.getItem() + ": " +
                                current + " < " + wantedTotal);
                    }
                })
                .thenExecuteAtEnd("ae2_craft_done", () -> helper.log(EventCategory.AE2,
                        "Completed AE2 craft for " + target.getCount() + "x " + target.getItem()));
    }
}
