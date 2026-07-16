package org.chatterjay.crafting_tracker.network.payloads;

import net.minecraft.network.FriendlyByteBuf;

public record C2SToggleRuntimeHighlight(boolean enable) {
    public static C2SToggleRuntimeHighlight decode(FriendlyByteBuf buffer) {
        return new C2SToggleRuntimeHighlight(buffer.readBoolean());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(enable);
    }
}
