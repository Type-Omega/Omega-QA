package com.typeomega.qa.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;

import com.typeomega.qa.spi.WandSelectionData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandDataNbtTest {

    @Test
    void roundTripsNbtAndTransformsSelectionAndLabels() {
        WandDataNbt.WandData initial = WandDataNbt.WandData.empty()
                .withPos1(new BlockPos(10, 64, 10))
                .withPos2(new BlockPos(14, 67, 14))
                .withExportInfo("typeomega", "steam_test")
                .withUpsertedLabel(new WandSelectionData.LabelEntry(
                        "controller", LabelRole.CONTROLLER.id(), new BlockPos(2, 1, 0), Direction.NORTH))
                .withUpsertedLabel(new WandSelectionData.LabelEntry(
                        "hatch", LabelRole.INPUT_HATCH.id(), new BlockPos(4, 1, 2), Direction.EAST));

        assertTrue(initial.hasBounds());
        assertEquals(new Vec3i(5, 4, 5), initial.size());

        CompoundTag saved = WandDataNbt.write(new CompoundTag(), initial);
        WandDataNbt.WandData restored = WandDataNbt.read(saved);
        assertEquals(initial, restored);

        WandDataNbt.WandData moved = restored.withMovedSelection(3, -2, 1);
        assertEquals(new BlockPos(13, 62, 11), moved.minPos());
        assertEquals(new Vec3i(5, 4, 5), moved.size());

        // Shrinking EAST face by -1 puts 'hatch' (at x=4) outside [0..3]
        WandDataNbt.WandData shrunk = restored.withResizedFace(Direction.EAST, -1);
        assertEquals(new Vec3i(4, 4, 5), shrunk.size());
        assertFalse(shrunk.isInsideSelection(new BlockPos(4, 1, 2)));
        assertTrue(shrunk.isInsideSelection(new BlockPos(2, 1, 0)));

        WandDataNbt.WandData renamed = shrunk.withRenamedLabel("hatch", "steam_hatch");
        assertTrue(renamed.labels().stream().anyMatch(l -> "steam_hatch".equals(l.name())));
        assertEquals(1, renamed.withRemovedLabel("steam_hatch").labels().size());
    }

    @Test
    void labelRolesResolveAndCycleCleanly() {
        assertEquals(LabelRole.ME_BUS, LabelRole.fromId("me_bus"));
        assertEquals(LabelRole.CUSTOM, LabelRole.fromId("unknown_role"));
        assertEquals(List.of(LabelRole.values()).size(), 11);
        assertEquals(LabelRole.ENERGY_HATCH, LabelRole.CONTROLLER.next());
    }
}
