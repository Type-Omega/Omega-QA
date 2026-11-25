package com.typeomega.qa.wand;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.typeomega.qa.command.OmegaQaCommand;
import com.typeomega.qa.net.FailureOverlaySyncPacket;
import com.typeomega.qa.net.OmegaQaNetwork;
import com.typeomega.qa.spi.DefaultFailureOverlaySink;
import com.typeomega.qa.spi.FailureDiagnostic;
import com.typeomega.qa.spi.FailureOverlaySink;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import com.typeomega.qa.spi.OmegaQaModule;
import com.typeomega.qa.visual.ClientVisualHooks;

import java.util.Map;
import java.util.Optional;

/**
 * Service-loaded {@link OmegaQaModule} wiring the Omega QA Wand item, network channel, {@code /omegaqa} commands,
 * synced {@link FailureOverlaySink}, and client-only level overlays/editor without modifying the lib.
 */
public final class QaWandModule implements OmegaQaModule {

    @Override
    @SuppressWarnings("removal")
    public void onInit() {
        QaWandTexts.init();
        QaWandRegistry.register(FMLJavaModLoadingContext.get().getModEventBus());
        OmegaQaNetwork.init();
        OmegaQaBootstrap.registerCommandRegistrar(new OmegaQaCommand());
        OmegaQaBootstrap.setOverlaySink(new SyncedFailureOverlaySink());
        MinecraftForge.EVENT_BUS.addListener(OmegaWandItem::onLeftClickBlock);

        // EN: Keep all net.minecraft.client.* references behind Dist.CLIENT so gameTestServer never loads client
        // classes.
        // PT: Isola referências net.minecraft.client.* atrás de Dist.CLIENT para o gameTestServer nunca carregar
        // classes client.
        // ZH: 将所有 net.minecraft.client.* 引用隔离在 Dist.CLIENT 后，避免 gameTestServer 加载客户端类。
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientVisualHooks::init);
    }

    /** Mirrors failure diagnostics both into server memory and connected clients. */
    private static final class SyncedFailureOverlaySink implements FailureOverlaySink {

        private final DefaultFailureOverlaySink memory = new DefaultFailureOverlaySink();

        @Override
        public void publish(FailureDiagnostic diagnostic) {
            memory.publish(diagnostic);
            OmegaQaNetwork.broadcastFailureSync(FailureOverlaySyncPacket.publish(diagnostic));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisualHooks.applyLocalDiagnostic(diagnostic));
        }

        @Override
        public void clearTest(String testId) {
            memory.clearTest(testId);
            OmegaQaNetwork.broadcastFailureSync(FailureOverlaySyncPacket.clearTest(testId));
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVisualHooks.clearLocalTest(testId));
        }

        @Override
        public void clearAll() {
            memory.clearAll();
            OmegaQaNetwork.broadcastFailureSync(FailureOverlaySyncPacket.clearAll());
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientVisualHooks::clearAllLocal);
        }

        @Override
        public Optional<FailureDiagnostic> latest(String testId) {
            return memory.latest(testId);
        }

        @Override
        public Map<String, FailureDiagnostic> activeDiagnostics() {
            return memory.activeDiagnostics();
        }
    }
}
