package org.chatterjay.craftingtracker.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import org.chatterjay.craftingtracker.CraftingTracker;
import org.chatterjay.craftingtracker.config.CTConfig;
import org.chatterjay.craftingtracker.item.NetworkLocatorItem;
import org.chatterjay.craftingtracker.network.ModNetwork;
import org.chatterjay.craftingtracker.util.ReflectionUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TrackerService {
    private static final Map<ProviderKey, Long> ACTIVE_SINCE = new HashMap<>();
    private static final Set<UUID> RUNTIME = new HashSet<>();
    private static final Set<UUID> DISABLED = new HashSet<>();
    private static int counter;

    private TrackerService() {}

    public static void tick(MinecraftServer server) {
        if (++counter % CTConfig.SCAN_INTERVAL.get() != 0) return;
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean enabled = isEnabled(player.getUUID());
            List<ModNetwork.ProviderEntry> providers = enabled ? scanProviders(player.serverLevel(), player.blockPosition(), now) : List.of();
            ModNetwork.sendProviders(player, providers, RUNTIME.contains(player.getUUID()));

            ItemStack locator = findLocator(player);
            if (locator.isEmpty()) {
                ModNetwork.sendLocator(player, Map.of());
                continue;
            }
            Map<BlockPos, List<ResourceLocation>> hits = scanLocator(player.serverLevel(), locator);
            ModNetwork.sendLocator(player, hits);
        }
    }

    public static boolean togglePlayer(UUID id) {
        if (DISABLED.remove(id)) return true;
        DISABLED.add(id);
        return false;
    }

    public static void setRuntimeEnabled(UUID id, boolean enabled) {
        if (enabled) RUNTIME.add(id); else RUNTIME.remove(id);
    }

    public static boolean isRuntimeEnabled(UUID id) { return RUNTIME.contains(id); }

    private static boolean isEnabled(UUID id) {
        return RUNTIME.contains(id) || (CTConfig.HIGHLIGHT_ENABLED.get() && !DISABLED.contains(id));
    }

    private static List<ModNetwork.ProviderEntry> scanProviders(ServerLevel level, BlockPos center, long now) {
        Set<Object> owners = collectOwners(level, center, CTConfig.SCAN_RADIUS.get());
        List<ModNetwork.ProviderEntry> result = new ArrayList<>();
        Set<ProviderKey> seen = new HashSet<>();
        for (Object owner : owners) {
            if (!ReflectionUtil.looksLikePatternProvider(owner)) continue;
            BlockPos pos = ReflectionUtil.getPosition(owner);
            if (pos == null || !pos.closerThan(center, CTConfig.SCAN_RADIUS.get() + 1.0)) continue;
            ReflectionUtil.PatternInfo patterns = ReflectionUtil.getPatternInfo(owner);
            boolean busy = ReflectionUtil.isBusy(owner);
            boolean requested = ReflectionUtil.isAnyRequested(owner, patterns.keys());
            boolean locked = ReflectionUtil.isLocked(owner);
            if (!busy && !requested && !locked) continue;

            ProviderKey key = new ProviderKey(level.dimension().location(), pos);
            seen.add(key);
            long start = ACTIVE_SINCE.computeIfAbsent(key, ignored -> now);
            long elapsed = now - start;
            int status = locked || elapsed >= CTConfig.STUCK_SECONDS.get() * 1000L ? 2
                    : elapsed >= CTConfig.STALL_SECONDS.get() * 1000L ? 1 : 0;
            result.add(new ModNetwork.ProviderEntry(pos, status, patterns.itemIds()));
        }
        ACTIVE_SINCE.keySet().removeIf(key -> key.dimension.equals(level.dimension().location()) && !seen.contains(key));
        return result;
    }

    private static Set<Object> collectOwners(ServerLevel level, BlockPos center, int radius) {
        Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        int chunkRadius = Math.max(1, (radius + 15) / 16);
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int x = cx + dx, z = cz + dz;
                if (!level.hasChunk(x, z)) continue;
                LevelChunk chunk = level.getChunk(x, z);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    owners.add(be);
                    Object grid = ReflectionUtil.findGrid(be);
                    if (grid != null && grids.add(grid)) owners.addAll(ReflectionUtil.gridOwners(grid));
                }
            }
        }
        return owners;
    }

    private static Map<BlockPos, List<ResourceLocation>> scanLocator(ServerLevel playerLevel, ItemStack locator) {
        BlockPos bound = NetworkLocatorItem.getBoundPos(locator);
        ResourceLocation dimension = NetworkLocatorItem.getBoundDimension(locator);
        if (bound == null || dimension == null || !dimension.equals(playerLevel.dimension().location())) return Map.of();
        List<ItemStack> filters = NetworkLocatorItem.getFilters(locator);
        if (filters.isEmpty()) return Map.of();

        Set<Object> owners = new HashSet<>();
        BlockEntity boundEntity = playerLevel.getBlockEntity(bound);
        Object grid = ReflectionUtil.findGrid(boundEntity);
        if (grid != null) owners.addAll(ReflectionUtil.gridOwners(grid));
        if (owners.isEmpty()) owners.addAll(collectOwners(playerLevel, bound, CTConfig.SCAN_RADIUS.get()));

        Map<BlockPos, List<ResourceLocation>> result = new LinkedHashMap<>();
        for (Object owner : owners) {
            BlockPos pos = ReflectionUtil.getPosition(owner);
            if (pos == null) continue;
            List<ResourceLocation> matches = new ArrayList<>();
            matchPatterns(owner, filters, matches);
            if (owner instanceof BlockEntity be) matchInventory(be, filters, matches);
            matchReflectiveInventories(owner, filters, matches);
            if (!matches.isEmpty()) result.put(pos, matches.subList(0, Math.min(3, matches.size())));
        }
        return result;
    }

    private static void matchPatterns(Object owner, List<ItemStack> filters, List<ResourceLocation> matches) {
        ReflectionUtil.PatternInfo info = ReflectionUtil.getPatternInfo(owner);
        for (ResourceLocation id : info.itemIds()) {
            if (matches.size() >= 3) return;
            for (ItemStack filter : filters) {
                if (BuiltInRegistries.ITEM.getKey(filter.getItem()).equals(id)) {
                    addUnique(matches, id);
                    break;
                }
            }
        }
    }

    private static void matchInventory(BlockEntity be, List<ItemStack> filters, List<ResourceLocation> matches) {
        for (Direction side : Direction.values()) inspectHandler(be.getCapability(ForgeCapabilities.ITEM_HANDLER, side), filters, matches);
        inspectHandler(be.getCapability(ForgeCapabilities.ITEM_HANDLER, null), filters, matches);
    }

    private static void inspectHandler(net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> optional,
                                       List<ItemStack> filters, List<ResourceLocation> matches) {
        optional.ifPresent(handler -> {
            for (int i = 0; i < handler.getSlots() && matches.size() < 3; i++) {
                ItemStack stack = handler.getStackInSlot(i);
                matchStack(stack, filters, matches);
            }
        });
    }

    private static void matchReflectiveInventories(Object owner, List<ItemStack> filters, List<ResourceLocation> matches) {
        for (String name : new String[]{"getConfig", "getStorage", "getInventory", "getInternalInventory"}) {
            Object inventory = ReflectionUtil.invoke(owner, name);
            if (inventory == null) continue;
            Object sizeValue = ReflectionUtil.invoke(inventory, "size", "getSlots", "getContainerSize");
            int size = sizeValue instanceof Number n ? n.intValue() : 0;
            for (int i = 0; i < size && matches.size() < 3; i++) {
                Object value = invokeIndexed(inventory, i, "getStackInSlot", "getItem", "getKey");
                if (value instanceof ItemStack stack) matchStack(stack, filters, matches);
                else {
                    ResourceLocation id = ReflectionUtil.resourceIdForKey(value);
                    if (id != null) {
                        for (ItemStack filter : filters) {
                            if (BuiltInRegistries.ITEM.getKey(filter.getItem()).equals(id)) addUnique(matches, id);
                        }
                    }
                }
            }
        }
    }

    private static Object invokeIndexed(Object target, int index, String... names) {
        for (String name : names) {
            Object value = ReflectionUtil.invokeOneArg(target, name, index);
            if (value != null) return value;
        }
        return null;
    }

    private static void matchStack(ItemStack stack, List<ItemStack> filters, List<ResourceLocation> matches) {
        if (stack == null || stack.isEmpty()) return;
        for (ItemStack filter : filters) {
            if (filter.is(stack.getItem())) {
                addUnique(matches, BuiltInRegistries.ITEM.getKey(stack.getItem()));
                return;
            }
        }
    }

    private static void addUnique(List<ResourceLocation> list, ResourceLocation id) {
        if (!list.contains(id)) list.add(id);
    }

    private static ItemStack findLocator(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) if (stack.is(CraftingTracker.NETWORK_LOCATOR.get())) return stack;
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(CraftingTracker.NETWORK_LOCATOR.get())) return stack;
        return ItemStack.EMPTY;
    }

    private record ProviderKey(ResourceLocation dimension, BlockPos pos) {}
}
