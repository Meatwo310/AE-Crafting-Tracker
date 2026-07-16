package org.chatterjay.crafting_tracker;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import org.chatterjay.crafting_tracker.config.CTConfig;
import org.chatterjay.crafting_tracker.item.NetworkLocatorMenu;
import org.chatterjay.crafting_tracker.item.NetworkLocatorTool;
import org.chatterjay.crafting_tracker.server.CraftTracker;
import org.chatterjay.crafting_tracker.server.CraftTrackerCommand;
import org.chatterjay.crafting_tracker.server.CraftTrackerNetwork;

@Mod(CraftingTracker.MOD_ID)
public final class CraftingTracker {
    public static final String MOD_ID = "crafting_tracker";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<Item> NETWORK_LOCATOR = ITEMS.register("network_locator",
            () -> new NetworkLocatorTool(new Item.Properties()));

    public static final RegistryObject<MenuType<NetworkLocatorMenu>> NETWORK_LOCATOR_MENU =
            MENUS.register("network_locator",
                    () -> new MenuType<>(NetworkLocatorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final RegistryObject<CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.crafting_tracker.tab"))
                    .icon(() -> NETWORK_LOCATOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(NETWORK_LOCATOR.get()))
                    .build());

    public CraftingTracker() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        MENUS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CTConfig.SPEC);
        modBus.addListener(CTConfig::onLoad);
        modBus.addListener(CTConfig::onReload);

        CraftTrackerNetwork.register();

        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CraftTracker.onServerTick(event.getServer());
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CraftTrackerCommand.register(event.getDispatcher());
    }
}
