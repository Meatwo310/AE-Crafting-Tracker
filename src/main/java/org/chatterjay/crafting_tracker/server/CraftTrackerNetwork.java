package org.chatterjay.crafting_tracker.server;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.chatterjay.crafting_tracker.Crafting_tracker;
import org.chatterjay.crafting_tracker.client.ClientHighlightCache;
import org.chatterjay.crafting_tracker.client.ClientLocatorCache;
import org.chatterjay.crafting_tracker.item.NetworkLocatorMenu;
import org.chatterjay.crafting_tracker.network.payloads.C2SUpdateFilterSlot;
import org.chatterjay.crafting_tracker.network.payloads.S2CCraftHighlightData;
import org.chatterjay.crafting_tracker.network.payloads.S2CLocatorHighlights;

import net.neoforged.fml.loading.FMLEnvironment;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class CraftTrackerNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Crafting_tracker.MODID);

        // Provider highlights
        registrar.playToClient(
                S2CCraftHighlightData.TYPE,
                S2CCraftHighlightData.STREAM_CODEC,
                (data, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientHighlightCache.INSTANCE.update(data));
                    }
                });

        // Locator highlights
        registrar.playToClient(
                S2CLocatorHighlights.TYPE,
                S2CLocatorHighlights.STREAM_CODEC,
                (data, context) -> {
                    if (FMLEnvironment.dist.isClient()) {
                        context.enqueueWork(() -> ClientLocatorCache.INSTANCE.update(data));
                    }
                });

        // Filter slot updates from client (EMI drag-drop, scroll-wheel clear)
        registrar.playToServer(
                C2SUpdateFilterSlot.TYPE,
                C2SUpdateFilterSlot.STREAM_CODEC,
                (data, context) -> {
                    context.enqueueWork(() -> {
                        var player = context.player();
                        if (player != null && player.containerMenu instanceof NetworkLocatorMenu menu) {
                            if (data.slotIndex() >= 0 && data.slotIndex() < 9) {
                                LOGGER.info("[Network] Filter slot {} update from player {}: {}",
                                        data.slotIndex(), player.getName().getString(), data.stack().isEmpty() ? "CLEAR" : data.stack().getItem());
                                menu.getFilterContainer().setItem(data.slotIndex(), data.stack());
                            }
                        }
                    });
                });
    }
}
