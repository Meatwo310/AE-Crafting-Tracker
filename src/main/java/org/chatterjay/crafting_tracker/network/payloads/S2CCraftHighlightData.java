package org.chatterjay.crafting_tracker.network.payloads;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record S2CCraftHighlightData(List<HighlightEntry> entries, int runtimeRemainingTicks) {

    public record HighlightEntry(BlockPos pos, int statusOrdinal, List<OutputItem> outputs) {
        public record OutputItem(ResourceLocation itemId, int outputType) {}
    }

    public static S2CCraftHighlightData decode(FriendlyByteBuf buffer) {
        return new S2CCraftHighlightData(readEntries(buffer), buffer.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (HighlightEntry entry : entries) {
            buf.writeBlockPos(entry.pos());
            buf.writeVarInt(entry.statusOrdinal());
            List<HighlightEntry.OutputItem> outputs = entry.outputs();
            buf.writeVarInt(outputs != null ? outputs.size() : 0);
            if (outputs != null) {
                for (HighlightEntry.OutputItem out : outputs) {
                    buf.writeResourceLocation(out.itemId());
                    buf.writeVarInt(out.outputType());
                }
            }
        }
        buf.writeVarInt(runtimeRemainingTicks);
    }

    private static List<HighlightEntry> readEntries(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<HighlightEntry> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BlockPos pos = buf.readBlockPos();
            int ordinal = buf.readVarInt();
            int outputCount = buf.readVarInt();
            List<HighlightEntry.OutputItem> outputs;
            if (outputCount > 0) {
                outputs = new ArrayList<>(outputCount);
                for (int j = 0; j < outputCount; j++) {
                    ResourceLocation itemId = buf.readResourceLocation();
                    int outputType = buf.readVarInt();
                    outputs.add(new HighlightEntry.OutputItem(itemId, outputType));
                }
            } else {
                outputs = List.of();
            }
            list.add(new HighlightEntry(pos, ordinal, outputs));
        }
        return list;
    }

}
