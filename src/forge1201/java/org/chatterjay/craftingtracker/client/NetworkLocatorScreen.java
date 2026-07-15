package org.chatterjay.craftingtracker.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.chatterjay.craftingtracker.item.NetworkLocatorMenu;
import org.chatterjay.craftingtracker.network.ModNetwork;

public final class NetworkLocatorScreen extends AbstractContainerScreen<NetworkLocatorMenu> {
    private Button runtimeButton;

    public NetworkLocatorScreen(NetworkLocatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
    }

    @Override
    protected void init() {
        super.init();
        runtimeButton = addRenderableWidget(Button.builder(runtimeLabel(), button -> {
            boolean next = !ClientState.runtime();
            ClientState.setRuntimeLocal(next);
            ModNetwork.sendRuntimeToggle(next);
            button.setMessage(runtimeLabel());
        }).bounds(leftPos + 112, topPos + 18, 54, 20).build());
    }

    private Component runtimeLabel() {
        return Component.translatable(ClientState.runtime()
                ? "button.crafting_tracker.runtime_highlight.on"
                : "button.crafting_tracker.runtime_highlight.off");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        graphics.fill(leftPos + 5, topPos + 14, leftPos + imageWidth - 5, topPos + 76, 0xD0202020);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int x = leftPos + 61 + col * 18;
                int y = topPos + 18 + row * 18;
                graphics.fill(x, y, x + 18, y + 18, 0xFF777777);
                graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF222222);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFFFFF, false);
        graphics.drawString(font, playerInventoryTitle, 8, 73, 0xAAAAAA, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
