package org.chatterjay.crafting_tracker.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

public record C2SUpdateFilterSlot(int slotIndex, ItemStack stack) {
    public C2SUpdateFilterSlot {
        stack = stack.copy();
        if (!stack.isEmpty()) {
            stack.setCount(1);
        }
    }

    public static C2SUpdateFilterSlot decode(FriendlyByteBuf buffer) {
        return new C2SUpdateFilterSlot(buffer.readVarInt(), buffer.readItem());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(slotIndex);
        buffer.writeItem(stack);
    }
}
