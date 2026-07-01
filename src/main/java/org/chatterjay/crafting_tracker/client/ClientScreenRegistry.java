package org.chatterjay.crafting_tracker.client;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import org.chatterjay.crafting_tracker.Crafting_tracker;
import org.chatterjay.crafting_tracker.client.screen.NetworkLocatorScreen;

public class ClientScreenRegistry {

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(Crafting_tracker.NETWORK_LOCATOR_MENU.get(), NetworkLocatorScreen::new);
    }
}
