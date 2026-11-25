package com.typeomega.qa;

import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.typeomega.lib.lang.TrLanguageProvider;
import com.typeomega.qa.spi.OmegaQaBootstrap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Omega QA: GameTest DSL, rigs, sweeps, reports, perf benches and the QA wand for the Type Omega mods. Loaded only in
 * dev, CI and test packs; players never need it.
 */
@Mod(OmegaQA.MOD_ID)
@SuppressWarnings("removal")
public final class OmegaQA {

    public static final String MOD_ID = "omegaqa";
    public static final Logger LOGGER = LogManager.getLogger("Omega QA");

    public OmegaQA() {
        OmegaQaBootstrap.init();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(OmegaQA::gatherData);
    }

    private static void gatherData(GatherDataEvent event) {
        TrLanguageProvider.addAll(event, MOD_ID, MOD_ID + ".", true);
    }
}
