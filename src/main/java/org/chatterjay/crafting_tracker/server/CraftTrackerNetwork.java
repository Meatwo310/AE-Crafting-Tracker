package org.chatterjay.crafting_tracker.server;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import org.chatterjay.crafting_tracker.CraftingTracker;
import org.chatterjay.crafting_tracker.client.ClientHighlightCache;
import org.chatterjay.crafting_tracker.client.ClientLocatorCache;
import org.chatterjay.crafting_tracker.item.NetworkLocatorMenu;
import org.chatterjay.crafting_tracker.network.payloads.C2SToggleRuntimeHighlight;
import org.chatterjay.crafting_tracker.network.payloads.C2SUpdateFilterSlot;
import org.chatterjay.crafting_tracker.network.payloads.S2CCraftHighlightData;
import org.chatterjay.crafting_tracker.network.payloads.S2CLocatorHighlights;

public final class CraftTrackerNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(CraftingTracker.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private CraftTrackerNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(S2CCraftHighlightData.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCraftHighlightData::encode)
                .decoder(S2CCraftHighlightData::decode)
                .consumerMainThread(CraftTrackerNetwork::handleCraftHighlights)
                .add();
        CHANNEL.messageBuilder(S2CLocatorHighlights.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLocatorHighlights::encode)
                .decoder(S2CLocatorHighlights::decode)
                .consumerMainThread(CraftTrackerNetwork::handleLocatorHighlights)
                .add();
        CHANNEL.messageBuilder(C2SUpdateFilterSlot.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SUpdateFilterSlot::encode)
                .decoder(C2SUpdateFilterSlot::decode)
                .consumerMainThread(CraftTrackerNetwork::handleFilterUpdate)
                .add();
        CHANNEL.messageBuilder(C2SToggleRuntimeHighlight.class, id, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SToggleRuntimeHighlight::encode)
                .decoder(C2SToggleRuntimeHighlight::decode)
                .consumerMainThread(CraftTrackerNetwork::handleRuntimeToggle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendToServer(Object message) {
        CHANNEL.sendToServer(message);
    }

    private static void handleCraftHighlights(S2CCraftHighlightData data,
            Supplier<NetworkEvent.Context> contextSupplier) {
        ClientHighlightCache.INSTANCE.update(data);
        contextSupplier.get().setPacketHandled(true);
    }

    private static void handleLocatorHighlights(S2CLocatorHighlights data,
            Supplier<NetworkEvent.Context> contextSupplier) {
        ClientLocatorCache.INSTANCE.update(data);
        contextSupplier.get().setPacketHandled(true);
    }

    private static void handleFilterUpdate(C2SUpdateFilterSlot data,
            Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = contextSupplier.get().getSender();
        if (player != null && player.containerMenu instanceof NetworkLocatorMenu menu
                && data.slotIndex() >= 0 && data.slotIndex() < 9) {
            menu.getFilterContainer().setItem(data.slotIndex(), data.stack());
        }
        contextSupplier.get().setPacketHandled(true);
    }

    private static void handleRuntimeToggle(C2SToggleRuntimeHighlight data,
            Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPlayer player = contextSupplier.get().getSender();
        if (player != null) {
            MinecraftServer server = player.getServer();
            if (data.enable()) {
                CraftTracker.enableRuntimeHighlight(player.getUUID(), server.overworld().getGameTime());
            } else {
                CraftTracker.disableRuntimeHighlight(player.getUUID());
                sendToPlayer(player, new S2CCraftHighlightData(List.of(), 0));
            }
        }
        contextSupplier.get().setPacketHandled(true);
    }
}
