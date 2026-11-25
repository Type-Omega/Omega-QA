package com.typeomega.qa.command;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.typeomega.qa.net.OmegaQaNetwork;
import com.typeomega.qa.net.ReportSyncPacket;
import com.typeomega.qa.net.SyncWandStatePacket;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.OmegaCommandRegistrar;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.OmegaReportSnapshot;
import com.typeomega.qa.spi.TestCaseRecord;
import com.typeomega.qa.wand.OmegaWandItem;
import com.typeomega.qa.wand.QaWandRegistry;
import com.typeomega.qa.wand.QaWandTexts;
import com.typeomega.qa.wand.StructureExportService;
import com.typeomega.qa.wand.WandDataNbt;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Registers the {@code /omegaqa} subcommand suite: {@code runall}, {@code run <selector>}, {@code wand},
 * {@code load <ns:name> [rot]}, {@code export <name> [ns]}, and {@code report}.
 */
public final class OmegaQaCommand implements OmegaCommandRegistrar {

    @Override
    public void registerSubcommands(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext buildContext) {
        root.then(Commands.literal("runall").executes(OmegaQaCommand::runAll))
                .then(Commands.literal("run")
                        .then(Commands.argument("selector", StringArgumentType.greedyString())
                                .executes(OmegaQaCommand::runSelected)))
                .then(Commands.literal("wand").executes(OmegaQaCommand::giveOrOpenWand))
                .then(Commands.literal("load")
                        .then(Commands.argument("template", ResourceLocationArgument.id())
                                .executes(ctx -> loadTemplate(ctx, Rotation.NONE))
                                .then(Commands.argument("rotation", StringArgumentType.word())
                                        .executes(ctx -> loadTemplate(ctx,
                                                parseRotation(StringArgumentType.getString(ctx, "rotation")))))))
                .then(Commands.literal("export")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> exportWand(ctx, WandDataNbt.DEFAULT_NAMESPACE))
                                .then(Commands.argument("namespace", StringArgumentType.word())
                                        .executes(ctx -> exportWand(ctx,
                                                StringArgumentType.getString(ctx, "namespace"))))))
                .then(Commands.literal("report").executes(OmegaQaCommand::openReport));
    }

    private static int runAll(CommandContext<CommandSourceStack> ctx) {
        Collection<TestFunction> all = GameTestRegistry.getAllTestFunctions();
        return executeTests(ctx.getSource(), all, "*");
    }

    private static int runSelected(CommandContext<CommandSourceStack> ctx) {
        String selector = StringArgumentType.getString(ctx, "selector").trim();
        String norm = selector.toLowerCase(Locale.ROOT);
        List<TestFunction> matched = GameTestRegistry.getAllTestFunctions().stream()
                .filter(fn -> matchesSelector(fn, norm))
                .toList();
        return executeTests(ctx.getSource(), matched, selector);
    }

    static boolean matchesSelector(TestFunction fn, String normSelector) {
        if (normSelector.isEmpty() || "*".equals(normSelector)) return true;
        String testName = fn.getTestName().toLowerCase(Locale.ROOT);
        String batch = fn.getBatchName().toLowerCase(Locale.ROOT);
        String structure = fn.getStructureName().toLowerCase(Locale.ROOT);
        if (testName.contains(normSelector) || batch.equals(normSelector) || batch.contains(normSelector)) {
            return true;
        }
        int colon = structure.indexOf(':');
        if (colon > 0) {
            String ns = structure.substring(0, colon);
            if (ns.equals(normSelector)) return true;
        }
        return false;
    }

    private static int executeTests(CommandSourceStack source, Collection<TestFunction> tests, String selector) {
        if (tests.isEmpty()) {
            source.sendFailure(QaWandTexts.CMD_NO_TESTS_MATCH.component(selector));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition()).offset(4, 2, 4);
        OmegaQaBootstrap.overlaySink().clearAll();

        Collection<GameTestInfo> infos = GameTestRunner.runTests(
                tests, origin, Rotation.NONE, level, GameTestTicker.SINGLETON, 8);
        for (GameTestInfo info : infos) {
            OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                    info.getTestName(), info.getStructureBlockPos(), info.getStructureSize(),
                    null, null, null, "running", FailureDiagnostic.TestStatus.RUNNING));
            info.addListener(new InGameTestListener());
        }

        source.sendSuccess(() -> QaWandTexts.CMD_RUNNING_TESTS.component(infos.size())
                .withStyle(ChatFormatting.GREEN), true);
        return infos.size();
    }

    private static int giveOrOpenWand(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(Component.literal("Player required."));
            return 0;
        }
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof OmegaWandItem) {
            WandDataNbt.WandData data = WandDataNbt.read(main);
            OmegaQaNetwork.sendToPlayer(player,
                    new SyncWandStatePacket(InteractionHand.MAIN_HAND, data.toSelectionData(), data.nextCorner(),
                            true));
            return 1;
        }
        ItemStack wand = new ItemStack(QaWandRegistry.OMEGA_WAND.get());
        if (!player.getInventory().add(wand)) {
            player.drop(wand, false);
        }
        ctx.getSource().sendSuccess(() -> QaWandTexts.CMD_WAND_GIVEN.component().withStyle(ChatFormatting.GREEN),
                false);
        return 1;
    }

    private static int loadTemplate(CommandContext<CommandSourceStack> ctx, Rotation rotation) {
        ResourceLocation templateId = ResourceLocationArgument.getId(ctx, "template");
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos origin = BlockPos.containing(ctx.getSource().getPosition()).offset(2, 0, 2);
        try {
            StructureExportService.LoadResult res = StructureExportService.loadStructure(
                    level, templateId, origin, rotation, StructureExportService.defaultExportRoot());
            if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                ItemStack held = player.getMainHandItem();
                if (!(held.getItem() instanceof OmegaWandItem)) {
                    held = new ItemStack(QaWandRegistry.OMEGA_WAND.get());
                    if (!player.getInventory().add(held)) {
                        player.drop(held, false);
                    }
                }
                if (held.getItem() instanceof OmegaWandItem) {
                    WandDataNbt.write(held, res.wandData());
                    OmegaQaNetwork.sendToPlayer(player, new SyncWandStatePacket(InteractionHand.MAIN_HAND,
                            res.wandData().toSelectionData(), res.wandData().nextCorner()));
                }
            }
            ctx.getSource().sendSuccess(() -> QaWandTexts.CMD_LOADED.component(
                    templateId.toString(),
                    res.size().getX() + "x" + res.size().getY() + "x" + res.size().getZ(),
                    OmegaWandItem.formatPos(origin),
                    res.wandData().labels().size()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Load failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int exportWand(CommandContext<CommandSourceStack> ctx, String namespace) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            ctx.getSource().sendFailure(QaWandTexts.CMD_NO_WAND.component());
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof OmegaWandItem)) {
            ctx.getSource().sendFailure(QaWandTexts.CMD_NO_WAND.component());
            return 0;
        }
        WandDataNbt.WandData data = WandDataNbt.read(stack);
        if (!data.hasBounds()) {
            ctx.getSource().sendFailure(QaWandTexts.CMD_NO_BOUNDS.component());
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        String resolvedNs = namespace.isBlank() ? data.exportNamespace() : namespace;
        try {
            var res = StructureExportService.exportStructure(player.serverLevel(), data, name, resolvedNs,
                    StructureExportService.defaultExportRoot());
            WandDataNbt.WandData updated = data.withExportInfo(res.templateId().getNamespace(),
                    res.templateId().getPath());
            WandDataNbt.write(stack, updated);
            OmegaQaNetwork.sendToPlayer(player,
                    new SyncWandStatePacket(InteractionHand.MAIN_HAND, updated.toSelectionData(),
                            updated.nextCorner()));

            ctx.getSource().sendSuccess(() -> QaWandTexts.CMD_EXPORTED.component(
                    res.templateId().toString(),
                    res.blockCount(),
                    res.labelCount(),
                    res.nbtPath().getParent().toString()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int openReport(CommandContext<CommandSourceStack> ctx) {
        OmegaReportSnapshot snapshot = OmegaQaBootstrap.reportSink().latestSnapshot();
        if (snapshot == null) {
            ctx.getSource().sendFailure(QaWandTexts.CMD_NO_REPORT.component());
            return 0;
        }
        ctx.getSource().sendSuccess(() -> QaWandTexts.CMD_REPORT_SUMMARY.component(
                snapshot.status(), snapshot.total(), snapshot.passed(), snapshot.failed(),
                snapshot.errors(), snapshot.skipped()).withStyle(ChatFormatting.AQUA), false);

        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            OmegaQaNetwork.sendToPlayer(player, new ReportSyncPacket(snapshot));
        }
        return 1;
    }

    private static Rotation parseRotation(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "90", "cw90", "clockwise_90" -> Rotation.CLOCKWISE_90;
            case "180", "cw180", "clockwise_180" -> Rotation.CLOCKWISE_180;
            case "270", "ccw90", "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static final class InGameTestListener implements GameTestListener {

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test) {
            OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                    test.getTestName(), test.getStructureBlockPos(), test.getStructureSize(),
                    null, null, null, "passed", FailureDiagnostic.TestStatus.PASSED));
            OmegaQaBootstrap.reportSink().recordResult(toRecord(test, FailureDiagnostic.TestStatus.PASSED, null));
        }

        @Override
        public void testFailed(GameTestInfo test) {
            Throwable err = test.getError();
            String msg = err == null ? "failed" :
                    (err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName());
            OmegaQaBootstrap.overlaySink().latest(test.getTestName()).ifPresentOrElse(
                    existing -> {},
                    () -> OmegaQaBootstrap.overlaySink().publish(new FailureDiagnostic(
                            test.getTestName(), test.getStructureBlockPos(), test.getStructureSize(),
                            null, null, null, msg, FailureDiagnostic.TestStatus.FAILED)));
            OmegaQaBootstrap.reportSink().recordResult(toRecord(test, FailureDiagnostic.TestStatus.FAILED, msg));
        }

        private static TestCaseRecord toRecord(GameTestInfo test, FailureDiagnostic.TestStatus status, String msg) {
            String full = test.getTestName();
            int dot = full.lastIndexOf('.');
            String cls = dot > 0 ? full.substring(0, dot) : full;
            String m = dot > 0 && dot + 1 < full.length() ? full.substring(dot + 1) : full;
            return new TestCaseRecord(full, cls, m, status, test.getRunTime(),
                    null, null, msg, null, null, null, List.of());
        }
    }
}
