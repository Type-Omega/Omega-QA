package com.typeomega.qa.gt;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.dsl.PosLabelResolver;
import com.typeomega.qa.log.EventCategory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic structure builder for GameTests supporting both GTCEu {@code Previews} and MC {@code .nbt} templates
 * with {@code .labels.json} sidecars, far-slot allocation, automatic chunk forcing + cleanup, and hatch substitution.
 */
public final class StructureRig {

    private final OmegaTestHelper helper;
    private final @Nullable MultiblockMachineDefinition previewDefinition;
    private final @Nullable ResourceLocation templateId;
    private final Rotation rotation;

    private BlockPos absoluteOrigin;
    private @Nullable Block replaceHatchesWith;
    private @Nullable MultiblockMachineDefinition removeNearbyDef;
    private int removeNearbyRange;
    private @Nullable UUID ownerUuid;
    private boolean forceChunks = true;
    private boolean clearBoxBeforePlace = true;

    private StructureRig(OmegaTestHelper helper, @Nullable MultiblockMachineDefinition previewDefinition,
                         @Nullable ResourceLocation templateId, Rotation rotation) {
        this.helper = helper;
        this.previewDefinition = previewDefinition;
        this.templateId = templateId;
        this.rotation = rotation;
        this.absoluteOrigin = helper.raw().absolutePos(new BlockPos(0, 1, 0));
    }

    public static StructureRig fromPreview(GameTestHelper helper, MultiblockMachineDefinition definition) {
        return new StructureRig(OmegaTestHelper.wrap(helper), Objects.requireNonNull(definition, "definition"), null,
                Rotation.NONE);
    }

    public static StructureRig fromTemplate(GameTestHelper helper, ResourceLocation templateId) {
        return fromTemplate(helper, templateId, Rotation.NONE);
    }

    public static StructureRig fromTemplate(GameTestHelper helper, String templateId) {
        ResourceLocation parsed = ResourceLocation.tryParse(templateId);
        if (parsed == null) throw new IllegalArgumentException("Invalid template ResourceLocation: " + templateId);
        return fromTemplate(helper, parsed, Rotation.NONE);
    }

    public static StructureRig fromTemplate(GameTestHelper helper, ResourceLocation templateId, Rotation rotation) {
        return new StructureRig(OmegaTestHelper.wrap(helper), null, Objects.requireNonNull(templateId, "templateId"),
                Objects.requireNonNull(rotation, "rotation"));
    }

    public StructureRig atRelative(BlockPos relOrigin) {
        this.absoluteOrigin = helper.raw().absolutePos(relOrigin);
        return this;
    }

    public StructureRig atAbsolute(BlockPos absOrigin) {
        this.absoluteOrigin = Objects.requireNonNull(absOrigin, "absOrigin");
        return this;
    }

    public StructureRig atFarSlot(int slot) {
        return atFarSlot(slot, 128, 3600, 200);
    }

    /** Places the structure in an isolated far slot so giant megastructures never collide with test cells. */
    public StructureRig atFarSlot(int slot, int spacing, int baseZ, int baseY) {
        BlockPos zero = helper.raw().absolutePos(BlockPos.ZERO);
        this.absoluteOrigin = new BlockPos(zero.getX() + spacing * slot, baseY, zero.getZ() + baseZ);
        return this;
    }

    public StructureRig replaceHatchesWith(@Nullable Block casing) {
        this.replaceHatchesWith = casing;
        return this;
    }

    public StructureRig removeControllersNearby(MultiblockMachineDefinition def, int range) {
        this.removeNearbyDef = def;
        this.removeNearbyRange = range;
        return this;
    }

    public StructureRig owner(@Nullable UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
        return this;
    }

    public StructureRig forceChunks(boolean force) {
        this.forceChunks = force;
        return this;
    }

    public StructureRig clearBoxBeforePlace(boolean clear) {
        this.clearBoxBeforePlace = clear;
        return this;
    }

    public PlacedStructure place() {
        if (previewDefinition != null) {
            return placePreview();
        }
        return placeTemplate();
    }

    private PlacedStructure placePreview() {
        var shapes = previewDefinition.getMatchingShapes();
        if (shapes.isEmpty()) {
            throw new AssertionError("No preview shapes found for " + previewDefinition.getId());
        }
        var shape = shapes.get(0).getBlocks();
        int nx = shape.length;
        int ny = shape[0].length;
        int nz = shape[0][0].length;
        Vec3i size = new Vec3i(nx, ny, nz);

        prepareFootprint(size);

        ServerLevel level = helper.level();
        List<BlockPos> placed = new ArrayList<>();
        BlockPos controllerPos = null;
        for (int x = 0; x < nx; x++) {
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    var info = shape[x][y][z];
                    if (info == null || info.getBlockState().isAir()) continue;
                    BlockPos abs = absoluteOrigin.offset(x, y, z);
                    BlockState state = info.getBlockState();
                    if (state.is(previewDefinition.getBlock())) {
                        controllerPos = abs;
                    } else if (replaceHatchesWith != null && state.getBlock() instanceof MetaMachineBlock) {
                        state = replaceHatchesWith.defaultBlockState();
                    }
                    level.setBlock(abs, state, 2 | 16);
                    placed.add(abs);
                }
            }
        }
        if (controllerPos != null && ownerUuid != null) {
            MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
            if (machine != null) machine.setOwnerUUID(ownerUuid);
        }
        helper.log(EventCategory.STRUCTURE,
                "Placed preview " + previewDefinition.getId() + " at " + absoluteOrigin + " size=" + size);
        return registerCleanup(new PlacedStructure(helper, absoluteOrigin, size, Rotation.NONE,
                PosLabelResolver.empty(), placed, controllerPos));
    }

    private PlacedStructure placeTemplate() {
        ServerLevel level = helper.level();
        StructureTemplate template = level.getStructureManager().get(templateId).orElseThrow(
                () -> new AssertionError("Missing structure template: " + templateId));
        Vec3i rotatedSize = template.getSize(rotation);
        prepareFootprint(rotatedSize);

        // EN: Offset anchor via getZeroPositionWithTransform so rotated blocks stay within [origin, origin + size).
        // PT: Ajusta a âncora via getZeroPositionWithTransform para blocos rotacionados ficarem em [origin,
        // origin+size).
        // ZH: 通过 getZeroPositionWithTransform 调整锚点，使旋转后的方块仍落在 [origin, origin + size) 内。
        BlockPos anchor = template.getZeroPositionWithTransform(absoluteOrigin, Mirror.NONE, rotation);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE);
        template.placeInWorld(level, anchor, anchor, settings, level.random, 2 | 16);

        PosLabelResolver labels = PosLabelResolver.load(level.getServer().getResourceManager(), templateId, rotation);
        helper.setLabelResolver(labels);

        List<BlockPos> placed = new ArrayList<>();
        BlockPos controllerPos = labels.has("controller") ? absoluteOrigin.offset(labels.resolve("controller")) : null;
        for (int x = 0; x < rotatedSize.getX(); x++) {
            for (int y = 0; y < rotatedSize.getY(); y++) {
                for (int z = 0; z < rotatedSize.getZ(); z++) {
                    BlockPos abs = absoluteOrigin.offset(x, y, z);
                    if (!level.getBlockState(abs).isAir()) {
                        placed.add(abs);
                    }
                }
            }
        }
        if (controllerPos != null && ownerUuid != null) {
            MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
            if (machine != null) machine.setOwnerUUID(ownerUuid);
        }
        helper.log(EventCategory.STRUCTURE,
                "Placed template " + templateId + " (" + rotation + ") at " + absoluteOrigin);
        return registerCleanup(new PlacedStructure(helper, absoluteOrigin, rotatedSize, rotation, labels, placed,
                controllerPos));
    }

    private void prepareFootprint(Vec3i size) {
        ServerLevel level = helper.level();
        if (forceChunks) {
            Set<ChunkPos> forced = new HashSet<>();
            int minCx = absoluteOrigin.getX() >> 4;
            int maxCx = (absoluteOrigin.getX() + Math.max(0, size.getX() - 1)) >> 4;
            int minCz = absoluteOrigin.getZ() >> 4;
            int maxCz = (absoluteOrigin.getZ() + Math.max(0, size.getZ() - 1)) >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    ChunkPos cp = new ChunkPos(cx, cz);
                    forced.add(cp);
                    level.setChunkForced(cx, cz, true);
                }
            }
            helper.afterTest(() -> {
                for (ChunkPos cp : forced) {
                    level.setChunkForced(cp.x, cp.z, false);
                }
            });
        }
        if (removeNearbyDef != null && removeNearbyRange > 0) {
            removeControllers(level, removeNearbyDef, absoluteOrigin, removeNearbyRange);
        }
        if (clearBoxBeforePlace) {
            // EN: runGameTestServer reuses the world across runs; clearing the box prevents leftover blocks/machines.
            // PT: runGameTestServer reaproveita o mundo entre execuções; limpar a caixa evita sobras de rodadas
            // antigas.
            // ZH: runGameTestServer 会在多次运行间复用世界；清空区域可防止旧运行残留方块干扰测试。
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = 0; x < size.getX(); x++) {
                for (int y = 0; y < size.getY(); y++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        BlockPos pos = absoluteOrigin.offset(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, air, 2 | 16);
                        }
                    }
                }
            }
        }
    }

    private PlacedStructure registerCleanup(PlacedStructure placed) {
        helper.afterTest(placed::clear);
        return placed;
    }

    private static void removeControllers(ServerLevel level, MultiblockMachineDefinition def, BlockPos center,
                                          int range) {
        List<BlockPos> found = new ArrayList<>();
        for (int cx = (center.getX() - range) >> 4; cx <= (center.getX() + range) >> 4; cx++) {
            for (int cz = (center.getZ() - range) >> 4; cz <= (center.getZ() + range) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof IMachineBlockEntity holder &&
                            holder.getMetaMachine().getDefinition() == def) {
                        found.add(entity.getBlockPos());
                    }
                }
            }
        }
        for (BlockPos pos : found) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
        }
    }
}
