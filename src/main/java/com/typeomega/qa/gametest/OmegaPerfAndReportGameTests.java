package com.typeomega.qa.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.gt.MachineRig;
import com.typeomega.qa.log.EventCategory;
import com.typeomega.qa.perf.PerfBenchRig;
import com.typeomega.qa.spi.DefaultTestReportSink;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.PerfBaselineRecord;
import com.typeomega.qa.sweeps.LangSweepOptions;
import com.typeomega.qa.sweeps.OmegaSweeps;

import java.util.regex.Pattern;

/**
 * Self-tests for {@link PerfBenchRig}, {@link OmegaSweeps}, and {@link OmegaQaBootstrap#reportSink()}.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(OmegaQA.MOD_ID)
public final class OmegaPerfAndReportGameTests {

    private static final String TEMPLATE = "qa_empty_5x5";

    private OmegaPerfAndReportGameTests() {}

    @GameTest(template = TEMPLATE, batch = "omegaqa_perf", timeoutTicks = 80)
    public static void perfBenchRigEmitsBaselineWithoutFixedNumericCap(GameTestHelper raw) {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        MachineRig macerator = MachineRig.place(raw, new BlockPos(2, 1, 2), GTMachines.MACERATOR, GTValues.LV)
                .fillEnergy()
                .insertItem(0, new ItemStack(Items.COBBLESTONE, 64));

        BlockPos abs = macerator.absPos();
        BoundingBox box = new BoundingBox(abs.getX(), abs.getY(), abs.getZ(), abs.getX(), abs.getY(), abs.getZ());

        PerfBenchRig.of(raw, "lib_self_lv_macerator")
                .warmupTicks(4)
                .measureTicks(12)
                .monitorBox(box)
                .onBenchTick(() -> macerator.fillEnergy())
                .requireFunctional(() -> h.assertTrue(macerator.machine().getDefinition() != null, "machine exists"))
                .runLive();

        h.afterTest(() -> {
            h.events().assertLogged(EventCategory.PERF, "BENCH lib_self_lv_macerator");
            if (OmegaQaBootstrap.reportSink() instanceof DefaultTestReportSink def) {
                PerfBaselineRecord rec = def.benchmarks().stream()
                        .filter(b -> "lib_self_lv_macerator".equals(b.benchmarkId()))
                        .findFirst().orElse(null);
                h.assertTrue(rec != null && rec.ticks() == 12, "PerfBaselineRecord must be emitted");
                h.assertTrue(rec.maxMs() >= rec.p99Ms() && rec.p99Ms() >= rec.p95Ms() && rec.p95Ms() >= rec.p50Ms(),
                        "Percentiles must be monotonic: " + rec);
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = "omegaqa_sweeps", timeoutTicks = 40)
    public static void sweepsVerifyLibLangBrandingAndRecipes(GameTestHelper raw) {
        OmegaSweeps.namesInAllLanguages(OmegaQA.MOD_ID, LangSweepOptions.defaults()).assertClean(raw, 4);
        OmegaSweeps.noForbiddenBrandTerms(OmegaQA.MOD_ID, Pattern.compile("Horizon-QA|GTIA|Infinity Ascension"))
                .assertClean(raw, 10);

        // Verify GTCEu's real GT machine recipes + negative vanilla barrier recipe detection
        var gtceuConflicts = OmegaSweeps.recipeLookupConflicts(raw, OmegaQA.MOD_ID);
        raw.assertTrue(gtceuConflicts.failures().isEmpty(), "lib has no recipe lookup conflicts");

        GTRecipe cleanGt = GTRecipeTypes.MACERATOR_RECIPES
                .recipeBuilder(ResourceLocation.tryBuild(OmegaQA.MOD_ID, "self_clean_recipe"))
                .inputItems(Items.COBBLESTONE, 1)
                .outputItems(Items.GRAVEL, 1)
                .duration(20)
                .EUt(8)
                .buildRawRecipe();
        GTRecipe barrierGt = GTRecipeTypes.MACERATOR_RECIPES
                .recipeBuilder(ResourceLocation.tryBuild(OmegaQA.MOD_ID, "self_barrier_recipe"))
                .inputItems(Items.BARRIER, 1)
                .outputItems(Items.GRAVEL, 1)
                .duration(20)
                .EUt(8)
                .buildRawRecipe();
        raw.assertTrue(cleanGt.getInputContents(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)
                .size() == 1 &&
                !barrierGt.getInputContents(
                        com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP).isEmpty(),
                "synthetic GT recipes built");

        var gtceuResult = OmegaSweeps.recipesHaveRealContents(raw, "gtceu");
        // Bare GTCEu 7.5.3 without pack mods leaves UEV+ vanilla crafting/smelting tags empty; verify GT machine
        // recipes themselves pass and the detector genuinely catches the empty-tag vanilla ones.
        var nonVanillaFailures = gtceuResult.failures().stream()
                .filter(f -> !f.startsWith("gtceu:shaped/") && !f.startsWith("gtceu:shapeless/") &&
                        !f.startsWith("gtceu:smelting/") && !f.startsWith("gtceu:blasting/") &&
                        !f.startsWith("gtceu:smoking/"))
                .toList();
        raw.assertTrue(gtceuResult.checkedCount() > 100 && !gtceuResult.failures().isEmpty() &&
                nonVanillaFailures.isEmpty(),
                "expected >100 GTCEu recipes with 0 GT failures, got " + nonVanillaFailures);
        raw.succeed();
    }
}
