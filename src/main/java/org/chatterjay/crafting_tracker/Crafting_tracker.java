package org.chatterjay.crafting_tracker;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.chatterjay.crafting_tracker.item.NetworkLocatorTool;

@Mod(Crafting_tracker.MODID)
public final class Crafting_tracker {
    public static final String MODID = "crafting_tracker";

    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Item> NETWORK_LOCATOR = ITEMS.register(
            "network_locator",
            () -> new NetworkLocatorTool(new Item.Properties().stacksTo(1))
    );

    public Crafting_tracker() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeTabContents);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NETWORK_LOCATOR);
        }
    }

    public static Component text(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
