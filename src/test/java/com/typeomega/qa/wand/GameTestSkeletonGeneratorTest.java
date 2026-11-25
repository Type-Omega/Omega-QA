package com.typeomega.qa.wand;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

import com.typeomega.qa.spi.WandSelectionData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameTestSkeletonGeneratorTest {

    @Test
    void generatesPascalCaseClassWithPositiveNegativeAndTrilingualComments() {
        ResourceLocation id = ResourceLocation.tryBuild("typeomega", "tokamak_hv_test");
        Vec3i size = new Vec3i(23, 17, 27);
        List<WandSelectionData.LabelEntry> labels = List.of(
                new WandSelectionData.LabelEntry("controller", LabelRole.CONTROLLER.id(),
                        new BlockPos(11, 2, 0), Direction.NORTH),
                new WandSelectionData.LabelEntry("energy_hatch", LabelRole.ENERGY_HATCH.id(),
                        new BlockPos(9, 2, 2), null),
                new WandSelectionData.LabelEntry("output_bus", LabelRole.OUTPUT_BUS.id(),
                        new BlockPos(13, 2, 2), null));

        assertEquals("TokamakHvTestGameTests", GameTestSkeletonGenerator.className("tokamak_hv_test"));

        String code = GameTestSkeletonGenerator.generate(id, size, labels);
        assertTrue(code.contains("@GameTestHolder(\"typeomega\")"));
        assertTrue(code.contains("@PrefixGameTestTemplate(false)"));
        assertTrue(code.contains("public final class TokamakHvTestGameTests"));
        assertTrue(code.contains("StructureRig.fromTemplate(raw, TEMPLATE)"));
        assertTrue(code.contains(".assertFormed()"));
        assertTrue(code.contains("rig.fillEnergyHatches();"));
        assertTrue(code.contains("rig.timeWarp()"));
        assertTrue(code.contains("placed.replaceBlock(placed.pos(\"energy_hatch\"), Blocks.AIR.defaultBlockState());"));
        assertTrue(code.contains("assertNeverForms(40, \"tokamak_hv_test_broken_never_forms\")"));
        assertTrue(code.contains("// EN:") && code.contains("// PT:") && code.contains("// ZH:"));
    }
}
