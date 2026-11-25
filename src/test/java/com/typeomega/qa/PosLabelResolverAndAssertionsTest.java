package com.typeomega.qa;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;

import com.typeomega.qa.dsl.OmegaAssertions;
import com.typeomega.qa.dsl.PosLabelResolver;
import com.typeomega.qa.spi.WandSelectionData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosLabelResolverAndAssertionsTest {

    @Test
    void resolvesKnownLabelsAndFailsWithLabelErrorWhenMissing() {
        PosLabelResolver resolver = PosLabelResolver.fromEntries(Map.of(
                "controller", new WandSelectionData.LabelEntry("controller", "controller",
                        new BlockPos(2, 1, 0), null)));
        assertEquals(new BlockPos(2, 1, 0), resolver.resolve("controller"));
        AssertionError err = assertThrows(AssertionError.class, () -> resolver.resolve("missing_bus"));
        assertTrue(err.getMessage().contains("LABEL_ERROR"));
    }

    @Test
    void resolvesDottedNbtPaths() {
        CompoundTag inner = new CompoundTag();
        inner.putInt("charge", 5000);
        CompoundTag root = new CompoundTag();
        root.put("ForgeData", inner);

        var tag = OmegaAssertions.resolvePath(root, "ForgeData.charge");
        assertTrue(tag instanceof IntTag);
        assertEquals(5000, ((IntTag) tag).getAsInt());
    }
}
