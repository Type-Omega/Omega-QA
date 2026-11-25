package com.typeomega.qa.compat.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.typeomega.qa.dsl.OmegaTestHelper;

/**
 * {@code ModList}-guarded helper for AE2 networks ({@code IGrid}, power, channels, storage, pattern encoding,
 * autocrafting, and GTCEu ME hatches/buffers). Safe to reference even when AE2 is not installed.
 */
public final class MeNetworkHelper {

    private final OmegaTestHelper helper;
    private final Ae2GridBridge bridge;

    private MeNetworkHelper(OmegaTestHelper helper, Ae2GridBridge bridge) {
        this.helper = helper;
        this.bridge = bridge;
    }

    public static MeNetworkHelper at(GameTestHelper helper, BlockPos relControllerOrNodePos) {
        OmegaTestHelper oh = OmegaTestHelper.wrap(helper);
        oh.assumeModLoaded("ae2");
        BlockPos abs = helper.absolutePos(relControllerOrNodePos);
        return new MeNetworkHelper(oh, Ae2GridBridge.resolve(oh, abs));
    }

    public MeNetworkHelper assertOnline() {
        bridge.assertOnline();
        return this;
    }

    public MeNetworkHelper assertChannelsUsed(int min, int max) {
        bridge.assertChannelsUsed(min, max);
        return this;
    }

    public MeNetworkHelper assertPowerStored(double minAe) {
        bridge.assertPowerStored(minAe);
        return this;
    }

    public MeNetworkHelper insertItem(ItemStack stack) {
        bridge.insertItem(stack);
        return this;
    }

    public MeNetworkHelper insertFluid(FluidStack fluid) {
        bridge.insertFluid(fluid);
        return this;
    }

    public MeNetworkHelper assertStored(Item item, long minAmount) {
        bridge.assertStored(item, minAmount);
        return this;
    }

    public MeNetworkHelper assertStoredFluid(FluidStack fluid) {
        bridge.assertStoredFluid(fluid);
        return this;
    }

    public MeNetworkHelper encodePattern(BlockPos relProviderPos, ItemStack[] inputs, ItemStack[] outputs) {
        bridge.encodePattern(helper.raw().absolutePos(relProviderPos), inputs, outputs);
        return this;
    }

    public MeNetworkHelper requestCraft(ItemStack target, int timeoutTicks) {
        bridge.requestCraft(target, timeoutTicks);
        return this;
    }
}
