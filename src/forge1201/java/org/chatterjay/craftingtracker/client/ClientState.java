package org.chatterjay.craftingtracker.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.chatterjay.craftingtracker.network.ModNetwork;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientState {
    private static final Map<BlockPos, ModNetwork.ProviderEntry> PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, List<ResourceLocation>> LOCATOR = new ConcurrentHashMap<>();
    private static volatile boolean runtime;

    private ClientState() {}

    public static void setProviders(List<ModNetwork.ProviderEntry> entries, boolean runtimeEnabled) {
        PROVIDERS.clear();
        entries.forEach(entry -> PROVIDERS.put(entry.pos(), entry));
        runtime = runtimeEnabled;
    }

    public static void setLocator(Map<BlockPos, List<ResourceLocation>> entries) {
        LOCATOR.clear();
        LOCATOR.putAll(entries);
    }

    public static Map<BlockPos, ModNetwork.ProviderEntry> providers() { return Map.copyOf(PROVIDERS); }
    public static Map<BlockPos, List<ResourceLocation>> locator() { return Map.copyOf(LOCATOR); }
    public static boolean runtime() { return runtime; }
    public static void setRuntimeLocal(boolean value) { runtime = value; }
    public static void clear() { PROVIDERS.clear(); LOCATOR.clear(); runtime = false; }
}
