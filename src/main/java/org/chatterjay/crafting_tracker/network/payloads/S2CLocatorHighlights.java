package org.chatterjay.crafting_tracker.network.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record S2CLocatorHighlights(Map<BlockPos, List<LocatorHit>> hits, int runtimeRemainingTicks) {

    public record LocatorHit(ResourceLocation itemId, int outputType) {}

    public static S2CLocatorHighlights decode(FriendlyByteBuf buffer) {
        return new S2CLocatorHighlights(readHits(buffer), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(hits.size());
        for (var entry : hits.entrySet()) {
            buf.writeBlockPos(entry.getKey());
            List<LocatorHit> list = entry.getValue();
            buf.writeVarInt(list.size());
            for (LocatorHit hit : list) {
                buf.writeResourceLocation(hit.itemId());
                buf.writeVarInt(hit.outputType());
            }
        }
        buf.writeVarInt(runtimeRemainingTicks);
    }

    private static Map<BlockPos, List<LocatorHit>> readHits(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<BlockPos, List<LocatorHit>> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            BlockPos pos = buf.readBlockPos();
            int hitCount = buf.readVarInt();
            List<LocatorHit> list = new ArrayList<>(hitCount);
            for (int j = 0; j < hitCount; j++) {
                ResourceLocation itemId = buf.readResourceLocation();
                int outputType = buf.readVarInt();
                list.add(new LocatorHit(itemId, outputType));
            }
            map.put(pos, list);
        }
        return map;
    }

}
