package com.typeomega.qa.perf;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.spi.OmegaQaBootstrap;

import java.util.Locale;

/**
 * {@code /omegaqa bench multi <machine> <count> <ticks>} places that many formed multiblocks from their preview and
 * logs {@code BENCH mspt=… p95=… p99=… worst=… heap_delta_mb=…} over the next ticks.
 */
public final class QaBenchCommand {

    private static long tickStart = -1;
    private static TickLatencySampler sampler;
    private static MemorySnapshot startMemory;

    private QaBenchCommand() {}

    public static void register() {
        OmegaQaBootstrap.registerCommandRegistrar((root, buildContext) -> root.then(Commands.literal("bench")
                .then(Commands.literal("multi")
                        .then(Commands.argument("machine", ResourceLocationArgument.id())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 512))
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(20, 72_000))
                                                .executes(QaBenchCommand::benchMulti)))))));
        MinecraftForge.EVENT_BUS.addListener(QaBenchCommand::onServerTick);
    }

    private static int benchMulti(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "machine");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
        MachineDefinition raw = GTRegistries.MACHINES.get(id);
        if (!(raw instanceof MultiblockMachineDefinition def)) {
            ctx.getSource().sendFailure(Component.literal("unknown multiblock " + id));
            return 0;
        }
        var shapes = def.getMatchingShapes();
        if (shapes.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("no preview shape for " + id));
            return 0;
        }
        var shape = shapes.get(0).getBlocks();
        ServerLevel level = ctx.getSource().getLevel();
        int stepX = shape.length + 2;
        int stepZ = shape[0][0].length + 2;
        BlockPos base = new BlockPos(0, 200, 2048);
        int formed = 0;
        for (int i = 0; i < count; i++) {
            BlockPos origin = base.offset((i % 8) * stepX, 0, (i / 8) * stepZ);
            for (int x = 0; x < shape.length; x++) {
                for (int y = 0; y < shape[x].length; y++) {
                    for (int z = 0; z < shape[x][y].length; z++) {
                        var info = shape[x][y][z];
                        if (info == null || info.getBlockState().isAir()) continue;
                        BlockPos pos = origin.offset(x, y, z);
                        ChunkPos cp = new ChunkPos(pos);
                        level.setChunkForced(cp.x, cp.z, true);
                        level.setBlock(pos, info.getBlockState(), 2 | 16);
                        if (info.getBlockState().is(def.getBlock()) &&
                                level.getBlockEntity(pos) instanceof IMachineBlockEntity be &&
                                be.getMetaMachine() instanceof MultiblockControllerMachine ctrl &&
                                ctrl.checkPatternWithLock()) {
                            ctrl.onStructureFormed();
                            formed++;
                        }
                    }
                }
            }
        }
        tickStart = -1;
        sampler = new TickLatencySampler(0, ticks);
        startMemory = MemorySnapshot.capture();
        int f = formed;
        ctx.getSource().sendSuccess(() -> Component.literal("placed " + count + " " + id + " (" + f +
                " formed), measuring " + ticks + " ticks"), false);
        return count;
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (sampler == null || sampler.isComplete()) return;
        if (event.phase == TickEvent.Phase.START) {
            tickStart = System.nanoTime();
            return;
        }
        if (tickStart < 0) return;
        boolean done = sampler.recordNanos(System.nanoTime() - tickStart);
        tickStart = -1;
        if (!done) return;
        TickLatencySampler.Stats stats = sampler.compute();
        MemorySnapshot.Delta delta = MemorySnapshot.capture().deltaSince(startMemory);
        OmegaQA.LOGGER.info(String.format(Locale.ROOT,
                "BENCH mspt=%.3f p95=%.3f p99=%.3f worst=%.3f heap_delta_mb=%.2f ticks=%d",
                stats.avgMs(), stats.p95Ms(), stats.p99Ms(), stats.maxMs(), delta.heapDeltaMb(), stats.samples()));
        sampler = null;
    }
}
