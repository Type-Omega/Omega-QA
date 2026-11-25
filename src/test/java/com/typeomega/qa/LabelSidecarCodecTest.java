package com.typeomega.qa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import com.typeomega.qa.spi.LabelSidecarCodec;
import com.typeomega.qa.spi.WandSelectionData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LabelSidecarCodecTest {

    @Test
    void roundTripsLabelsJsonAndAppliesRotations() {
        ResourceLocation templateId = ResourceLocation.tryBuild("typeomega", "tokamak_hv_test");
        Vec3i size = new Vec3i(10, 5, 8);
        List<WandSelectionData.LabelEntry> labels = List.of(
                new WandSelectionData.LabelEntry("controller", "controller", new BlockPos(0, 1, 0), Direction.NORTH),
                new WandSelectionData.LabelEntry("energy_hatch", "energy_hatch", new BlockPos(9, 2, 7), null));

        String json = LabelSidecarCodec.encode(templateId, size, labels);
        LabelSidecarCodec.SidecarDocument decoded = LabelSidecarCodec.decode(json);

        assertEquals(LabelSidecarCodec.SCHEMA_VERSION, decoded.schemaVersion());
        assertEquals(templateId.toString(), decoded.template());
        assertEquals(size, decoded.size());
        assertEquals(2, decoded.labels().size());

        Map<String, WandSelectionData.LabelEntry> rot90 = LabelSidecarCodec.asMap(decoded, Rotation.CLOCKWISE_90);
        assertEquals(new BlockPos(7, 1, 0), rot90.get("controller").relativePos());
        assertEquals(Direction.EAST, rot90.get("controller").facing());
        assertEquals(new BlockPos(0, 2, 9), rot90.get("energy_hatch").relativePos());

        Map<String, WandSelectionData.LabelEntry> rot180 = LabelSidecarCodec.asMap(decoded, Rotation.CLOCKWISE_180);
        assertEquals(new BlockPos(9, 1, 7), rot180.get("controller").relativePos());
        assertEquals(Direction.SOUTH, rot180.get("controller").facing());
    }
}
