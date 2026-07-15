package org.chatterjay.craftingtracker.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.chatterjay.craftingtracker.CraftingTracker;
import org.chatterjay.craftingtracker.client.ClientState;
import org.chatterjay.craftingtracker.server.TrackerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(CraftingTracker.MOD_ID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();
    private static int nextId;

    private ModNetwork() {}

    public static void init() {
        CHANNEL.messageBuilder(S2CProviderHighlights.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CProviderHighlights::encode).decoder(S2CProviderHighlights::decode)
                .consumerMainThread(S2CProviderHighlights::handle).add();
        CHANNEL.messageBuilder(S2CLocatorHighlights.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLocatorHighlights::encode).decoder(S2CLocatorHighlights::decode)
                .consumerMainThread(S2CLocatorHighlights::handle).add();
        CHANNEL.messageBuilder(C2SRuntimeToggle.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SRuntimeToggle::encode).decoder(C2SRuntimeToggle::decode)
                .consumerMainThread(C2SRuntimeToggle::handle).add();
    }

    public static void sendProviders(ServerPlayer player, List<ProviderEntry> entries, boolean runtime) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CProviderHighlights(entries, runtime));
    }

    public static void sendLocator(ServerPlayer player, Map<BlockPos, List<ResourceLocation>> hits) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CLocatorHighlights(hits));
    }

    public static void sendRuntimeToggle(boolean enabled) {
        CHANNEL.sendToServer(new C2SRuntimeToggle(enabled));
    }

    public record ProviderEntry(BlockPos pos, int status, List<ResourceLocation> outputs) {
        private static ProviderEntry decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int status = buf.readVarInt();
            int size = buf.readVarInt();
            List<ResourceLocation> outputs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) outputs.add(buf.readResourceLocation());
            return new ProviderEntry(pos, status, outputs);
        }

        private void encode(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(status);
            buf.writeVarInt(outputs.size());
            outputs.forEach(buf::writeResourceLocation);
        }
    }

    public record S2CProviderHighlights(List<ProviderEntry> entries, boolean runtime) {
        private static void encode(S2CProviderHighlights msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.entries.size());
            msg.entries.forEach(entry -> entry.encode(buf));
            buf.writeBoolean(msg.runtime);
        }

        private static S2CProviderHighlights decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            List<ProviderEntry> entries = new ArrayList<>(size);
            for (int i = 0; i < size; i++) entries.add(ProviderEntry.decode(buf));
            return new S2CProviderHighlights(entries, buf.readBoolean());
        }

        private static void handle(S2CProviderHighlights msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientState.setProviders(msg.entries, msg.runtime));
            ctx.get().setPacketHandled(true);
        }
    }

    public record S2CLocatorHighlights(Map<BlockPos, List<ResourceLocation>> hits) {
        private static void encode(S2CLocatorHighlights msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.hits.size());
            for (var entry : msg.hits.entrySet()) {
                buf.writeBlockPos(entry.getKey());
                buf.writeVarInt(entry.getValue().size());
                entry.getValue().forEach(buf::writeResourceLocation);
            }
        }

        private static S2CLocatorHighlights decode(FriendlyByteBuf buf) {
            int size = buf.readVarInt();
            Map<BlockPos, List<ResourceLocation>> hits = new HashMap<>();
            for (int i = 0; i < size; i++) {
                BlockPos pos = buf.readBlockPos();
                int count = buf.readVarInt();
                List<ResourceLocation> ids = new ArrayList<>(count);
                for (int j = 0; j < count; j++) ids.add(buf.readResourceLocation());
                hits.put(pos, ids);
            }
            return new S2CLocatorHighlights(hits);
        }

        private static void handle(S2CLocatorHighlights msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> ClientState.setLocator(msg.hits));
            ctx.get().setPacketHandled(true);
        }
    }

    public record C2SRuntimeToggle(boolean enabled) {
        private static void encode(C2SRuntimeToggle msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.enabled); }
        private static C2SRuntimeToggle decode(FriendlyByteBuf buf) { return new C2SRuntimeToggle(buf.readBoolean()); }
        private static void handle(C2SRuntimeToggle msg, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ctx.get().enqueueWork(() -> TrackerService.setRuntimeEnabled(player.getUUID(), msg.enabled));
            ctx.get().setPacketHandled(true);
        }
    }
}
