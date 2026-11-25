package com.typeomega.qa.gametest.wand;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.OmegaTestHelper;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.LabelSidecarCodec;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.TestCaseRecord;
import com.typeomega.qa.spi.WandSelectionData;
import com.typeomega.qa.wand.GameTestSkeletonGenerator;
import com.typeomega.qa.wand.LabelRole;
import com.typeomega.qa.wand.OmegaWandItem;
import com.typeomega.qa.wand.QaWandRegistry;
import com.typeomega.qa.wand.StructureExportService;
import com.typeomega.qa.wand.WandDataNbt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Headless GameTests validating {@link OmegaWandItem}, {@code /omegaqa wand|export|load|report}, {@code .nbt} +
 * {@code .labels.json} creation, and {@link GameTestSkeletonGenerator} output.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(OmegaQA.MOD_ID)
public final class OmegaWandExportGameTests {

    private static final String TEMPLATE = "qa_empty_5x5";

    private OmegaWandExportGameTests() {}

    @GameTest(template = TEMPLATE, batch = "omegaqa_wand", timeoutTicks = 40)
    public static void omegaQaCommandExportsNbtLabelsAndGameTestSkeleton(GameTestHelper raw) throws IOException {
        OmegaTestHelper h = OmegaTestHelper.wrap(raw);
        BlockPos absOrigin = raw.absolutePos(new BlockPos(1, 1, 1));
        raw.setBlock(new BlockPos(1, 1, 1), Blocks.IRON_BLOCK.defaultBlockState());
        raw.setBlock(new BlockPos(2, 1, 1), Blocks.GOLD_BLOCK.defaultBlockState());
        raw.setBlock(new BlockPos(3, 2, 2), Blocks.GLASS.defaultBlockState());

        ServerPlayer tester = h.fakePlayer("wand_exporter");
        tester.setPos(absOrigin.getX(), absOrigin.getY(), absOrigin.getZ());
        tester.getInventory().clearContent();

        CommandSourceStack source = tester.createCommandSourceStack().withPermission(4);
        var commands = raw.getLevel().getServer().getCommands();

        int wandRes = commands.performPrefixedCommand(source, "omegaqa wand");
        h.assertTrue(wandRes > 0, "/omegaqa wand must succeed");

        ItemStack held = findWandInInventory(tester);
        h.assertTrue(held.getItem() instanceof OmegaWandItem, "player must receive OmegaWandItem");
        tester.setItemInHand(InteractionHand.MAIN_HAND, held);

        WandDataNbt.WandData data = WandDataNbt.WandData.empty()
                .withPos1(absOrigin)
                .withPos2(absOrigin.offset(2, 1, 1))
                .withUpsertedLabel(new WandSelectionData.LabelEntry(
                        "controller", LabelRole.CONTROLLER.id(), new BlockPos(0, 0, 0), Direction.NORTH))
                .withUpsertedLabel(new WandSelectionData.LabelEntry(
                        "energy_hatch", LabelRole.ENERGY_HATCH.id(), new BlockPos(1, 0, 0), Direction.SOUTH));
        WandDataNbt.write(held, data);

        String exportName = "wand_smoke_rig";
        String exportNs = OmegaQA.MOD_ID;
        Path exportDir = StructureExportService.defaultExportRoot().resolve(exportNs);
        Path nbtPath = exportDir.resolve(exportName + ".nbt");
        Path labelsPath = exportDir.resolve(exportName + ".labels.json");
        Path javaPath = exportDir.resolve(GameTestSkeletonGenerator.className(exportName) + ".java");

        h.afterTest(() -> {
            try {
                Files.deleteIfExists(nbtPath);
                Files.deleteIfExists(labelsPath);
                Files.deleteIfExists(javaPath);
            } catch (IOException ignored) {}
        });

        int exportRes = commands.performPrefixedCommand(source, "omegaqa export " + exportName + " " + exportNs);
        h.assertTrue(exportRes > 0, "/omegaqa export must succeed");
        h.assertTrue(Files.exists(nbtPath) && Files.size(nbtPath) > 0, "exported .nbt must exist");
        h.assertTrue(Files.exists(labelsPath), "exported .labels.json must exist");
        h.assertTrue(Files.exists(javaPath), "generated *GameTests.java must exist");

        LabelSidecarCodec.SidecarDocument doc = LabelSidecarCodec.read(labelsPath);
        h.assertTrue(doc.labels().size() == 2, "sidecar must preserve both semantic labels");
        String skeleton = Files.readString(javaPath);
        h.assertTrue(skeleton.contains("@GameTestHolder(\"" + exportNs + "\")") &&
                skeleton.contains("assertNeverForms(") &&
                skeleton.contains("// EN:") && skeleton.contains("// PT:") && skeleton.contains("// ZH:"),
                "generated skeleton must contain positive, negative, and trilingual annotations");

        // Verify /omegaqa load places the exported structure back and repopulates Wand labels
        var loaded = StructureExportService.loadStructure(raw.getLevel(),
                ResourceLocation.tryBuild(exportNs, exportName),
                absOrigin.offset(0, 3, 0), Rotation.CLOCKWISE_90, StructureExportService.defaultExportRoot());
        h.assertTrue(loaded.wandData().labels().size() == 2, "loaded structure must restore labels");

        // Verify /omegaqa report works headlessly when a snapshot exists
        OmegaQaBootstrap.reportSink().recordResult(new TestCaseRecord(
                "omegaqa.wand_self_check", "OmegaWandExportGameTests", "smoke",
                FailureDiagnostic.TestStatus.PASSED, 1L, null, null, null, null, null, null, List.of()));
        int reportRes = commands.performPrefixedCommand(source, "omegaqa report");
        h.assertTrue(reportRes > 0, "/omegaqa report must succeed when snapshot exists");
        h.succeed();
    }

    private static ItemStack findWandInInventory(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() == QaWandRegistry.OMEGA_WAND.get()) return s;
        }
        return ItemStack.EMPTY;
    }
}
