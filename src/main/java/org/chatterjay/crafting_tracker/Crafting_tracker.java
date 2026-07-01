package org.chatterjay.crafting_tracker;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import org.chatterjay.crafting_tracker.config.CTConfig;
import org.chatterjay.crafting_tracker.item.NetworkLocatorMenu;
import org.chatterjay.crafting_tracker.item.NetworkLocatorTool;
import org.chatterjay.crafting_tracker.server.CraftTracker;
import org.chatterjay.crafting_tracker.server.CraftTrackerCommand;
import org.chatterjay.crafting_tracker.server.CraftTrackerNetwork;
import org.slf4j.Logger;

@Mod(Crafting_tracker.MODID)
public class Crafting_tracker {
    public static final String MODID = "crafting_tracker";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);

    // Items
    public static final DeferredItem<Item> NETWORK_LOCATOR = ITEMS.register("network_locator",
            () -> new NetworkLocatorTool(new Item.Properties()));

    // Menus
    public static final java.util.function.Supplier<MenuType<NetworkLocatorMenu>> NETWORK_LOCATOR_MENU =
            MENUS.register("network_locator", () -> new MenuType<>(NetworkLocatorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // Creative tab
    public static final java.util.function.Supplier<CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> NETWORK_LOCATOR.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(NETWORK_LOCATOR.get()))
                    .build());

    public Crafting_tracker(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, CTConfig.SPEC);

        modEventBus.addListener(RegisterPayloadHandlersEvent.class,
                (event) -> CraftTrackerNetwork.register(event));

        // Client-only: register screens
        modEventBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class,
                org.chatterjay.crafting_tracker.client.ClientScreenRegistry::registerScreens);

        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
                (event) -> CraftTracker.onServerTick(event.getServer()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                (event) -> CraftTrackerCommand.register(event.getDispatcher()));

        // Client-only: log EMI availability at startup for debugging
        modEventBus.addListener(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent.class, event -> {
            event.enqueueWork(() -> {
                if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
                    LOGGER.info("[Locator] EMI detected, plugin will load via META-INF/services");
                } else {
                    LOGGER.info("[Locator] EMI not installed, drag-drop disabled");
                }
            });
        });
    }
}
