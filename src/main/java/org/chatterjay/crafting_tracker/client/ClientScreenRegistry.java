package org.chatterjay.crafting_tracker.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import org.chatterjay.crafting_tracker.CraftingTracker;
import org.chatterjay.crafting_tracker.client.handler.CraftingScreenHandler;
import org.chatterjay.crafting_tracker.client.screen.NetworkLocatorScreen;

@Mod.EventBusSubscriber(modid = CraftingTracker.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientScreenRegistry {
    private ClientScreenRegistry() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(CraftingTracker.NETWORK_LOCATOR_MENU.get(), NetworkLocatorScreen::new);
            CraftingScreenHandler.register();
        });
    }
}
