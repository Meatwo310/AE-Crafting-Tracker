package org.chatterjay.crafting_tracker.client.handler;

import appeng.client.gui.me.crafting.CraftingStatusScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import org.chatterjay.crafting_tracker.network.payloads.C2SToggleRuntimeHighlight;
import org.chatterjay.crafting_tracker.server.CraftTrackerNetwork;

public final class CraftingScreenHandler {
    private static final int BUTTON_X = 43;
    private static final int BUTTON_Y = 160;
    private static final int BUTTON_W = 50;
    private static final int BUTTON_H = 20;

    private static boolean runtimeActive;

    private CraftingScreenHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(CraftingScreenHandler::onScreenInit);
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof CraftingStatusScreen craftingScreen)) return;

        Button button = Button.builder(getButtonMessage(), ignored -> onClick(craftingScreen))
                .bounds(craftingScreen.getGuiLeft() + BUTTON_X, craftingScreen.getGuiTop() + BUTTON_Y,
                        BUTTON_W, BUTTON_H)
                .build();
        event.addListener(button);
    }

    private static Component getButtonMessage() {
        return Component.translatable(runtimeActive
                ? "button.crafting_tracker.runtime_highlight.on"
                : "button.crafting_tracker.runtime_highlight.off");
    }

    private static void onClick(CraftingStatusScreen screen) {
        runtimeActive = !runtimeActive;
        CraftTrackerNetwork.sendToServer(new C2SToggleRuntimeHighlight(runtimeActive));
        if (screen.getFocused() != null) {
            screen.setFocused(null);
        }
    }
}
