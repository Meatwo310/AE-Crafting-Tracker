package org.chatterjay.craftingtracker.item;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.chatterjay.craftingtracker.CraftingTracker;

public final class NetworkLocatorMenu extends AbstractContainerMenu {
    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer filters = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            persist();
        }
    };

    public static NetworkLocatorMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf data) {
        return new NetworkLocatorMenu(id, inventory, data.readEnum(InteractionHand.class));
    }

    public NetworkLocatorMenu(int id, Inventory inventory, InteractionHand hand) {
        super(CraftingTracker.NETWORK_LOCATOR_MENU.get(), id);
        this.player = inventory.player;
        this.hand = hand;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                addSlot(new Slot(filters, index, 62 + col * 18, 19 + row * 18) {
                    @Override public boolean mayPickup(Player player) { return false; }
                    @Override public int getMaxStackSize() { return 1; }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));

        ItemStack locator = locatorStack();
        for (int i = 0; i < 9; i++) filters.setItem(i, NetworkLocatorItem.getFilter(locator, i));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < 9) {
            Slot slot = getSlot(slotId);
            if (button == 1 || getCarried().isEmpty()) slot.set(ItemStack.EMPTY);
            else {
                ItemStack copy = getCarried().copy();
                copy.setCount(1);
                slot.set(copy);
            }
            broadcastChanges();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void persist() {
        if (player.level().isClientSide) return;
        ItemStack locator = locatorStack();
        if (locator.isEmpty()) return;
        for (int i = 0; i < 9; i++) NetworkLocatorItem.setFilter(locator, i, filters.getItem(i));
    }

    public ItemStack locatorStack() {
        ItemStack stack = player.getItemInHand(hand);
        return stack.is(CraftingTracker.NETWORK_LOCATOR.get()) ? stack : ItemStack.EMPTY;
    }

    public SimpleContainer filters() { return filters; }

    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) { return !locatorStack().isEmpty(); }
}
