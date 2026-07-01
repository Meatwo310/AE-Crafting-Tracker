package org.chatterjay.crafting_tracker.network.payloads;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.chatterjay.crafting_tracker.Crafting_tracker;

public record C2SUpdateFilterSlot(int slotIndex, ItemStack stack) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Crafting_tracker.MODID, "update_filter_slot");
    public static final Type<C2SUpdateFilterSlot> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SUpdateFilterSlot> STREAM_CODEC =
            StreamCodec.ofMember(C2SUpdateFilterSlot::write, C2SUpdateFilterSlot::new);

    public C2SUpdateFilterSlot(RegistryFriendlyByteBuf buf) {
        this(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(slotIndex);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
