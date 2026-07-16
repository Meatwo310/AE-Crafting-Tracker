package org.chatterjay.crafting_tracker.compat.emi;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;

import org.chatterjay.crafting_tracker.client.screen.NetworkLocatorScreen;
import org.chatterjay.crafting_tracker.network.payloads.C2SUpdateFilterSlot;

import org.chatterjay.crafting_tracker.server.CraftTrackerNetwork;

/**
 * EMI integration for Network Locator ghost slots.
 *
 * EMI discovers this plugin through its entrypoint annotation on Forge.
 */
@EmiEntrypoint
public class NetworkLocatorEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(NetworkLocatorScreen.class, new EmiDragDropHandler<NetworkLocatorScreen>() {

            @Override
            public boolean dropStack(NetworkLocatorScreen screen, EmiIngredient ingredient, int x, int y) {
                var slot = screen.getSlotUnderMouse();
                if (slot == null) return false;

                // Only handle ghost slots (indices 0-8)
                if (slot.index < 0 || slot.index >= 9) {
                    return false;
                }

                var stacks = ingredient.getEmiStacks();
                if (stacks.isEmpty()) return false;

                ItemStack stack = stacks.get(0).getItemStack();
                if (stack.isEmpty()) return false;

                ItemStack copy = stack.copy();
                copy.setCount(1);
                slot.set(copy);
                CraftTrackerNetwork.sendToServer(new C2SUpdateFilterSlot(slot.index, copy));
                return true;
            }

            @Override
            public void render(NetworkLocatorScreen screen, EmiIngredient ingredient,
                               GuiGraphics graphics, int mouseX, int mouseY, float delta) {
                // Show green highlight over ghost slots when dragging a valid item
                var slot = screen.getSlotUnderMouse();
                if (slot != null && slot.index >= 0 && slot.index < 9) {
                    var stacks = ingredient.getEmiStacks();
                    if (!stacks.isEmpty() && !stacks.get(0).getItemStack().isEmpty()) {
                        int x = screen.getGuiLeft() + slot.x;
                        int y = screen.getGuiTop() + slot.y;
                        graphics.fill(x, y, x + 16, y + 16, 0x8822BB33);
                    }
                }
            }
        });

    }
}
