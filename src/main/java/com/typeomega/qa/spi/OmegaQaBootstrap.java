package com.typeomega.qa.spi;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.typeomega.qa.OmegaQA;
import com.typeomega.qa.dsl.OmegaTestSession;
import com.typeomega.qa.report.OmegaQaProperties;
import com.typeomega.qa.report.OmegaTestReporter;
import com.typeomega.qa.sweeps.ProgressionSnapshotExporter;

import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Single bootstrap entry point for Omega-QA invoked from {@link OmegaQA}. Coordinates SPIs, {@code /omegaqa} command
 * registrars, reporter lifecycle, and discovered {@link OmegaQaModule} extensions.
 */
public final class OmegaQaBootstrap {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static final AtomicReference<FailureOverlaySink> OVERLAY_SINK = new AtomicReference<>(
            new DefaultFailureOverlaySink());
    private static final AtomicReference<TestReportSink> REPORT_SINK = new AtomicReference<>(
            new DefaultTestReportSink());
    private static final List<OmegaCommandRegistrar> COMMAND_REGISTRARS = new CopyOnWriteArrayList<>();
    private static final List<WirelessEnergyHook> WIRELESS_HOOKS = new CopyOnWriteArrayList<>();
    private static final List<SpecialHatchInspector> HATCH_INSPECTORS = new CopyOnWriteArrayList<>();
    private static final List<CropInspector> CROP_INSPECTORS = new CopyOnWriteArrayList<>();

    private OmegaQaBootstrap() {}

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) return;

        // EN: Register core QA command subcommands and tick/server lifecycle hooks before loading external SPI modules.
        // PT: Registra subcomandos centrais de QA e hooks de ciclo de vida antes de carregar módulos SPI externos.
        // ZH: 在加载外部 SPI 模块之前注册核心 QA 子命令和服务器生命周期钩子。
        MinecraftForge.EVENT_BUS.addListener(OmegaQaBootstrap::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(OmegaQaBootstrap::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(OmegaQaBootstrap::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(OmegaQaBootstrap::onServerTick);

        com.typeomega.qa.perf.QaBenchCommand.register();

        for (OmegaQaModule module : ServiceLoader.load(OmegaQaModule.class, OmegaQaBootstrap.class.getClassLoader())) {
            try {
                module.onInit();
            } catch (Throwable t) {
                OmegaQA.LOGGER.error("Failed to initialize OmegaQaModule {}", module.getClass().getName(), t);
            }
        }
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("omegaqa")
                .requires(source -> source.hasPermission(2));
        for (OmegaCommandRegistrar registrar : COMMAND_REGISTRARS) {
            registrar.registerSubcommands(root, event.getBuildContext());
        }
        event.getDispatcher().register(root);
    }

    private static void onServerStarting(ServerStartingEvent event) {
        OmegaTestReporter.installIfConfigured();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (OmegaQaProperties.exportSnapshot()) {
            ProgressionSnapshotExporter.export(event.getServer(), OmegaQaProperties.snapshotDir());
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        OmegaTestSession.onServerTick(event);
    }

    public static FailureOverlaySink overlaySink() {
        return OVERLAY_SINK.get();
    }

    public static void setOverlaySink(FailureOverlaySink sink) {
        OVERLAY_SINK.set(Objects.requireNonNull(sink, "overlaySink"));
    }

    public static TestReportSink reportSink() {
        return REPORT_SINK.get();
    }

    public static void setReportSink(TestReportSink sink) {
        REPORT_SINK.set(Objects.requireNonNull(sink, "reportSink"));
    }

    public static void registerCommandRegistrar(OmegaCommandRegistrar registrar) {
        if (registrar != null && !COMMAND_REGISTRARS.contains(registrar)) {
            COMMAND_REGISTRARS.add(registrar);
        }
    }

    public static void registerWirelessEnergyHook(WirelessEnergyHook hook) {
        if (hook != null && !WIRELESS_HOOKS.contains(hook)) WIRELESS_HOOKS.add(hook);
    }

    public static List<WirelessEnergyHook> wirelessEnergyHooks() {
        return List.copyOf(WIRELESS_HOOKS);
    }

    public static void registerSpecialHatchInspector(SpecialHatchInspector inspector) {
        if (inspector != null && !HATCH_INSPECTORS.contains(inspector)) HATCH_INSPECTORS.add(inspector);
    }

    public static List<SpecialHatchInspector> specialHatchInspectors() {
        return List.copyOf(HATCH_INSPECTORS);
    }

    public static void registerCropInspector(CropInspector inspector) {
        if (inspector != null && !CROP_INSPECTORS.contains(inspector)) CROP_INSPECTORS.add(inspector);
    }

    public static List<CropInspector> cropInspectors() {
        return List.copyOf(CROP_INSPECTORS);
    }
}
