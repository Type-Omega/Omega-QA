package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.dsl.PosLabelResolver;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handle for a structure placed by {@link StructureRig}, exposing semantic labels, block lookup, bounding box,
 * and {@link MultiblockRig} creation.
 */
public final class PlacedStructure implements AutoCloseable {

    private final OmegaTestHelper helper;
    private final BlockPos origin;
    private final Vec3i size;
    private final Rotation rotation;
    private final PosLabelResolver labels;
    private final List<BlockPos> placedPositions;
    private @Nullable BlockPos controllerPos;

    PlacedStructure(OmegaTestHelper helper, BlockPos origin, Vec3i size, Rotation rotation, PosLabelResolver labels,
                    List<BlockPos> placedPositions, @Nullable BlockPos controllerPos) {
        this.helper = helper;
        this.origin = origin;
        this.size = size;
        this.rotation = rotation;
        this.labels = labels;
        this.placedPositions = new ArrayList<>(placedPositions);
        this.controllerPos = controllerPos;
    }

    public OmegaTestHelper helper() {
        return helper;
    }

    public BlockPos origin() {
        return origin;
    }

    public Vec3i size() {
        return size;
    }

    public Rotation rotation() {
        return rotation;
    }

    public BoundingBox boundingBox() {
        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + Math.max(0, size.getX() - 1),
                origin.getY() + Math.max(0, size.getY() - 1),
                origin.getZ() + Math.max(0, size.getZ() - 1));
    }

    public PosLabelResolver labels() {
        return labels;
    }

    /** Resolves a semantic label from {@code .labels.json} to an absolute world {@link BlockPos}. */
    public BlockPos pos(String label) {
        return origin.offset(labels.resolve(label));
    }

    public BlockPos at(int relX, int relY, int relZ) {
        return origin.offset(relX, relY, relZ);
    }

    public List<BlockPos> findBlock(Block block) {
        ServerLevel level = helper.level();
        List<BlockPos> found = new ArrayList<>();
        for (int z = 0; z < size.getZ(); z++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockPos abs = origin.offset(x, y, z);
                    if (level.getBlockState(abs).is(block)) found.add(abs);
                }
            }
        }
        return found;
    }

    public void replaceBlock(BlockPos absPos, BlockState state) {
        helper.level().setBlock(absPos, state, 3);
    }

    public @Nullable BlockPos controllerPos() {
        if (controllerPos != null) return controllerPos;
        if (labels.has("controller")) return pos("controller");
        return null;
    }

    public MultiblockControllerMachine controller() {
        BlockPos pos = controllerPos();
        if (pos == null) {
            throw new AssertionError("No controller position recorded or labeled 'controller' in structure at " +
                    origin);
        }
        MetaMachine machine = MetaMachine.getMachine(helper.level(), pos);
        if (machine instanceof MultiblockControllerMachine ctrl) return ctrl;
        if (helper.level().getBlockEntity(pos) instanceof IMachineBlockEntity be &&
                be.getMetaMachine() instanceof MultiblockControllerMachine ctrl) {
            return ctrl;
        }
        throw new AssertionError("No MultiblockControllerMachine found at " + pos);
    }

    public MultiblockRig multiblock() {
        return MultiblockRig.of(helper, controller(), this);
    }

    public void clear() {
        ServerLevel level = helper.level();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < size.getX(); x++) {
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2 | 16);
                    }
                }
            }
        }
        placedPositions.clear();
    }

    @Override
    public void close() {
        clear();
    }
}
