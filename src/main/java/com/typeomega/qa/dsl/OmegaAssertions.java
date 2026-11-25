package com.typeomega.qa.dsl;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;

import java.util.Objects;
import java.util.function.Predicate;

/** Rich assertion primitives for items, fluids, and block entity NBT paths. */
public final class OmegaAssertions {

    private OmegaAssertions() {}

    public static void assertItemEqual(ItemStack expected, ItemStack actual) {
        if (expected.isEmpty() && actual.isEmpty()) return;
        if (!ItemStack.matches(expected, actual)) {
            throw new AssertionError("Expected ItemStack " + describeItem(expected) + " but got " +
                    describeItem(actual));
        }
    }

    public static void assertFluidEqual(FluidStack expected, FluidStack actual) {
        if (expected.isEmpty() && actual.isEmpty()) return;
        if (!expected.isFluidStackIdentical(actual)) {
            throw new AssertionError("Expected FluidStack " + describeFluid(expected) + " but got " +
                    describeFluid(actual));
        }
    }

    public static void assertBlockEntityNbt(Level level, BlockPos absPos, String nbtPath, Predicate<Tag> check,
                                            String desc) {
        BlockEntity be = level.getBlockEntity(absPos);
        if (be == null) {
            throw new AssertionError("Expected BlockEntity at " + absPos + " for NBT check '" + desc + "'");
        }
        CompoundTag root = be.saveWithoutMetadata();
        Tag resolved = resolvePath(root, nbtPath);
        if (resolved == null || !check.test(resolved)) {
            throw new AssertionError("NBT assertion failed (" + desc + ") at " + absPos + " path='" + nbtPath +
                    "', found=" + resolved);
        }
    }

    public static Tag resolvePath(CompoundTag root, String dottedPath) {
        if (root == null || dottedPath == null || dottedPath.isBlank()) return root;
        String[] parts = dottedPath.split("\\.");
        Tag current = root;
        for (String part : parts) {
            if (!(current instanceof CompoundTag compound) || !compound.contains(part)) {
                return null;
            }
            current = compound.get(part);
        }
        return current;
    }

    private static String describeItem(ItemStack stack) {
        if (stack.isEmpty()) return "<empty>";
        return stack.getCount() + "x " + stack.getItem() +
                (stack.hasTag() ? Objects.toString(stack.getTag()) : "");
    }

    private static String describeFluid(FluidStack stack) {
        if (stack.isEmpty()) return "<empty>";
        return stack.getAmount() + "mB " + stack.getFluid() +
                (stack.hasTag() ? Objects.toString(stack.getTag()) : "");
    }
}
