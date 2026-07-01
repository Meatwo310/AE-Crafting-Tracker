package org.chatterjay.crafting_tracker.compat.emi;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;

import org.chatterjay.crafting_tracker.client.screen.NetworkLocatorScreen;
import org.chatterjay.crafting_tracker.network.payloads.C2SUpdateFilterSlot;
import org.slf4j.Logger;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * EMI integration for Network Locator ghost slots.
 *
 * IMPORTANT: Uses @EmiEntrypoint annotation (not META-INF/services) because
 * EMI on NeoForge discovers plugins via ModFileScanData annotation scanning.
 */
@EmiEntrypoint
public class NetworkLocatorEmiPlugin implements EmiPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int SLOT_SIZE = 18;

    @Override
    public void register(EmiRegistry registry) {
        LOGGER.info("[LocatorEMI] Registering drag-drop handler for NetworkLocatorScreen");

        // Register a generic drag-drop handler so we also get render() callbacks for highlighting
        registry.addDragDropHandler(NetworkLocatorScreen.class, new EmiDragDropHandler<NetworkLocatorScreen>() {

            @Override
            public boolean dropStack(NetworkLocatorScreen screen, EmiIngredient ingredient, int x, int y) {
                var slot = screen.getSlotUnderMouse();
                if (slot == null) {
                    LOGGER.info("[LocatorEMI] No slot under mouse at ({}, {})", x, y);
                    return false;
                }

                // Only handle ghost slots (indices 0-8)
                if (slot.index < 0 || slot.index >= 9) {
                    return false;
                }

                var stacks = ingredient.getEmiStacks();
                if (stacks.isEmpty()) {
                    LOGGER.info("[LocatorEMI] Ingredient has no EmiStacks");
                    return false;
                }

                ItemStack stack = stacks.get(0).getItemStack();
                if (stack.isEmpty()) {
                    LOGGER.info("[LocatorEMI] First EmiStack produced empty ItemStack");
                    return false;
                }

                ItemStack copy = stack.copyWithCount(1);
                LOGGER.info("[LocatorEMI] Dropping {} into ghost slot {}", copy.getItem(), slot.index);
                slot.set(copy);
                PacketDistributor.sendToServer(new C2SUpdateFilterSlot(slot.index, copy));
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

        LOGGER.info("[LocatorEMI] Handler registered successfully");
    }
}
