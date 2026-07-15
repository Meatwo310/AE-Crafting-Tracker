package org.chatterjay.craftingtracker;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.extensions.IForgeMenuType;
import org.chatterjay.craftingtracker.client.ClientEvents;
import org.chatterjay.craftingtracker.config.CTConfig;
import org.chatterjay.craftingtracker.item.NetworkLocatorItem;
import org.chatterjay.craftingtracker.item.NetworkLocatorMenu;
import org.chatterjay.craftingtracker.network.ModNetwork;
import org.chatterjay.craftingtracker.server.TrackerService;

@Mod(CraftingTracker.MOD_ID)
public final class CraftingTracker {
    public static final String MOD_ID = "crafting_tracker";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<Item> NETWORK_LOCATOR = ITEMS.register("network_locator",
            () -> new NetworkLocatorItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<MenuType<NetworkLocatorMenu>> NETWORK_LOCATOR_MENU = MENUS.register(
            "network_locator",
            () -> IForgeMenuType.create((IContainerFactory<NetworkLocatorMenu>) NetworkLocatorMenu::fromNetwork));

    public CraftingTracker() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(this::addCreativeTabContents);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CTConfig.SPEC);
        ModNetwork.init();

        MinecraftForge.EVENT_BUS.addListener(this::serverTick);
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientEvents.bootstrap(modBus));
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(NETWORK_LOCATOR);
        }
    }

    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TrackerService.tick(event.getServer());
        }
    }

    private void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("crafttracker")
                .then(Commands.literal("toggle").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    boolean enabled = TrackerService.togglePlayer(player.getUUID());
                    player.sendSystemMessage(Component.translatable(enabled
                            ? "chat.crafting_tracker.enabled"
                            : "chat.crafting_tracker.disabled"));
                    return enabled ? 1 : 0;
                }))
                .then(Commands.literal("runtime").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    boolean enabled = !TrackerService.isRuntimeEnabled(player.getUUID());
                    TrackerService.setRuntimeEnabled(player.getUUID(), enabled);
                    player.sendSystemMessage(Component.literal("Crafting tracker runtime: " + (enabled ? "on" : "off")));
                    return enabled ? 1 : 0;
                })));
    }
}
