package com.typeomega.qa.wand;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.typeomega.qa.OmegaQA;

/**
 * Deferred registry for {@code typeomegalib:omega_wand}.
 */
public final class QaWandRegistry {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, OmegaQA.MOD_ID);

    public static final RegistryObject<OmegaWandItem> OMEGA_WAND = ITEMS.register("omega_wand",
            () -> new OmegaWandItem(new Item.Properties().stacksTo(1)));

    private QaWandRegistry() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(QaWandRegistry::addToCreativeTabs);
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES ||
                event.getTabKey() == CreativeModeTabs.OP_BLOCKS) {
            event.accept(OMEGA_WAND);
        }
    }
}
