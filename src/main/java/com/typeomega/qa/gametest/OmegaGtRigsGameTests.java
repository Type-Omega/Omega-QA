package com.typeomega.qa.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.gt.MachineRig;
import com.typeomega.qa.gt.MultiblockRig;
import com.typeomega.qa.gt.PlacedStructure;
import com.typeomega.qa.gt.StructureRig;
import com.typeomega.qa.gt.TempRecipeHandle;
import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.worldgen.WorldgenProbeRig;

import java.util.List;

/**
 * Positive and negative GameTests validating {@link StructureRig}, {@link MultiblockRig}, {@link MachineRig},
 * {@code TimeWarpRunner}, {@link TempRecipeHandle}, and {@link WorldgenProbeRig}.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(OmegaQA.MOD_ID)
public final class OmegaGtRigsGameTests {

    private static final String TEMPLATE = "qa_empty_5x5";

    private OmegaGtRigsGameTests() {}

    @GameTest(template = TEMPLATE, batch = "omegaqa_gt", timeoutTicks = 40)
    public static void ebfFormsCleanlyAndMachineTimeWarpCompletesRecipe(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        PlacedStructure placed = StructureRig.fromPreview(raw, GTMultiMachines.ELECTRIC_BLAST_FURNACE)
                .atFarSlot(1, 64, 3200, 200)
                .place();
        MultiblockRig ebf = placed.multiblock()
                .assertFormed()
                .fixMaintenance()
                .assertMaintenanceClean()
                .fillEnergyHatches();
        h.assertTrue(!ebf.energyHatches().isEmpty(), "EBF preview must expose energy hatches");

        // Run a real LV Alloy Smelter copper + tin -> bronze cycle via TimeWarpRunner
        MachineRig alloy = MachineRig.place(raw, new BlockPos(2, 1, 2), GTMachines.ALLOY_SMELTER, GTValues.LV)
                .fillEnergy()
                .insertItem(0, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Copper, 3))
                .insertItem(1, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Tin, 1))
                .findRecipe();

        alloy.timeWarp()
                .keepEnergyFull()
                .warpUntilIdle(400);

        int bronze = alloy.outputItemCount(ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Bronze).getItem());
        h.assertTrue(bronze == 4, "expected 4 bronze ingots from alloy smelter time-warp, got " + bronze);
        h.events().assertLogged(EventCategory.RECIPE, "RecipeStarted:");
        h.events().assertLogged(EventCategory.RECIPE, "RecipeFinished:");
        h.succeed();
    }

    /** Negative test: removing one heating coil prevents EBF formation and publishes PatternError diagnostic. */
    @GameTest(template = TEMPLATE, batch = "omegaqa_gt", timeoutTicks = 40)
    public static void brokenEbfNeverFormsAndPublishesDiagnostic(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        PlacedStructure placed = StructureRig.fromPreview(raw, GTMultiMachines.ELECTRIC_BLAST_FURNACE)
                .atFarSlot(2, 64, 3200, 200)
                .place();
        List<BlockPos> coils = placed.findBlock(GTBlocks.COIL_CUPRONICKEL.get());
        h.assertTrue(!coils.isEmpty(), "EBF preview must contain cupronickel coils");
        placed.replaceBlock(coils.get(0), Blocks.AIR.defaultBlockState());

        MultiblockRig ebf = placed.multiblock();
        boolean threw = false;
        try {
            ebf.assertFormed();
        } catch (AssertionError expected) {
            threw = true;
        }
        h.assertTrue(threw, "assertFormed must throw when a coil is missing");
        FailureDiagnostic diag = OmegaQaBootstrap.overlaySink().latest(h.session().testId()).orElse(null);
        h.assertTrue(diag != null && diag.failPos() != null,
                "FailureOverlaySink must receive PatternError failPos");
        ebf.assertNeverForms(12, "broken_ebf_never_forms");
    }

    @GameTest(template = TEMPLATE, batch = "omegaqa_gt", timeoutTicks = 40)
    public static void tempRecipeHandleRestoresRealRecipesAfterClose(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        GTRecipe temp = GTRecipeTypes.ALLOY_SMELTER_RECIPES
                .recipeBuilder(ResourceLocation.tryBuild(OmegaQA.MOD_ID, "temp_stick_torch"))
                .inputItems(Items.STICK, 1)
                .inputItems(Items.COAL, 1)
                .outputItems(Items.TORCH, 4)
                .duration(20)
                .EUt(16)
                .buildRawRecipe();

        try (TempRecipeHandle ignored = TempRecipeHandle.stage(h, GTRecipeTypes.ALLOY_SMELTER_RECIPES, temp)) {
            MachineRig alloy = MachineRig.place(raw, new BlockPos(2, 1, 2), GTMachines.ALLOY_SMELTER, GTValues.LV)
                    .fillEnergy()
                    .insertItem(0, new ItemStack(Items.STICK, 1))
                    .insertItem(1, new ItemStack(Items.COAL, 1));
            alloy.assertRunningRecipe(temp.getId());
        }

        // Verify real GTCEu alloy smelter lookup still works after TempRecipeHandle restored it
        MachineRig verifyReal = MachineRig.place(raw, new BlockPos(2, 1, 2), GTMachines.ALLOY_SMELTER, GTValues.LV)
                .fillEnergy()
                .insertItem(0, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Copper, 3))
                .insertItem(1, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Tin, 1))
                .findRecipe();
        h.assertTrue(verifyReal.recipeLogic().getLastRecipe() != null,
                "Real alloy smelter recipes must remain active after TempRecipeHandle closes");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "omegaqa_gt", timeoutTicks = 40)
    public static void worldgenProbeValidatesRegisteredVeinsAndChunks(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        h.assertTrue(!GTRegistries.ORE_VEINS.entries().isEmpty(), "GTCEu ORE_VEINS must be populated");
        ResourceLocation sampleVein = ResourceLocation.tryBuild("gtceu", "magnetite_vein_end");
        WorldgenProbeRig.assertVeinContains(sampleVein, GTMaterials.Magnetite, GTMaterials.Gold);
        WorldgenProbeRig.assertVeinExcludes(sampleVein, GTMaterials.Naquadah);

        var summary = WorldgenProbeRig.scanChunks(raw, new ChunkPos(raw.absolutePos(BlockPos.ZERO)), 0, 0, 16,
                WorldgenProbeRig.blockIdContains("_ore"), state -> false);
        h.assertTrue(summary.chunksScanned() == 1, "expected 1 chunk scanned, got " + summary.chunksScanned());
        h.succeed();
    }
}
