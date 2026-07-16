package org.chatterjay.crafting_tracker.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.SimpleContainer;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.chatterjay.crafting_tracker.api.CraftStatus;
import org.chatterjay.crafting_tracker.config.CTConfig;
import org.chatterjay.crafting_tracker.item.NetworkLocatorTool;
import org.chatterjay.crafting_tracker.network.payloads.S2CCraftHighlightData;
import org.chatterjay.crafting_tracker.network.payloads.S2CCraftHighlightData.HighlightEntry;
import org.chatterjay.crafting_tracker.network.payloads.S2CLocatorHighlights;
import org.chatterjay.crafting_tracker.server.CraftTrackerNetwork;
import org.slf4j.Logger;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.config.LockCraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingService;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

public class CraftTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_MISSED = 10;
    private static final long COOLDOWN_MS = 1000;

    private static final Set<UUID> disabledPlayers = new HashSet<>();
    private static final Map<UUID, Long> runtimeHighlightExpiry = new HashMap<>();
    /** Tracks players who have explicitly enabled runtime mode via the button. */
    private static final Set<UUID> runtimeActivePlayers = new HashSet<>();
    /** Tracks players who explicitly cancelled runtime via the button.
     *  These players get highlights disabled regardless of config setting,
     *  until they press "Run" again or the config changes. */
    private static final Set<UUID> runtimeExplicitlyDisabled = new HashSet<>();
    private static final Map<ProviderKey, TrackerEntry> entries = new HashMap<>();
    private static final Map<ProviderKey, Boolean> prevProviderBusy = new HashMap<>();
    /** Tracks which players had a locator in the most recent scan tick (for per-tick drop detection). */
    private static final Set<UUID> playersWithLocatorLastTick = new HashSet<>();
    /** Tracks each locator's last binding so network and dimension changes clear stale highlights. */
    private static final Map<UUID, LocatorBinding> lastBindings = new HashMap<>();
    private static int scanCounter;
    /** Independent counter for locator Phase 4 scan (increments every tick regardless of tracking state). */
    private static int locatorTickCounter;

    static final int TYPE_ITEM = 0;
    static final int TYPE_FLUID = 1;
    private static final int MAX_OUTPUTS = 3;

    private record OutputItem(ResourceLocation id, int type) {}
    private record LocatorBinding(ResourceLocation dimension, BlockPos pos) {}
    private record ProviderKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private static boolean isPatternSource(BlockEntity be) {
        return be instanceof PatternProviderLogicHost;
    }

    private static boolean isPatternBusy(BlockEntity be) {
        if (be instanceof PatternProviderLogicHost host) return host.getLogic().isBusy();
        return false;
    }

    private static boolean isPatternLocked(BlockEntity be) {
        if (be instanceof PatternProviderLogicHost host)
            return host.getLogic().getCraftingLockedReason() != LockCraftingMode.NONE;
        return false;
    }

    private static List<IPatternDetails> getPatterns(BlockEntity be) {
        if (be instanceof PatternProviderLogicHost host) return host.getLogic().getAvailablePatterns();
        return List.of();
    }

    @Nullable
    private static IGrid getGrid(BlockEntity be) {
        IGridNode node = getGridNode(be);
        return node != null ? node.getGrid() : null;
    }

    public static boolean isEnabledFor(UUID playerId) {
        if (runtimeActivePlayers.contains(playerId)) return true;
        if (runtimeExplicitlyDisabled.contains(playerId)) return false;
        return CTConfig.highlightEnabled && !disabledPlayers.contains(playerId);
    }

    public static void setEnabledFor(UUID playerId, boolean enabled) {
        if (enabled) {
            disabledPlayers.remove(playerId);
            runtimeExplicitlyDisabled.remove(playerId);
        } else {
            disabledPlayers.add(playerId);
        }
    }

    public static boolean isRuntimeActive(UUID playerId) {
        return runtimeHighlightExpiry.containsKey(playerId);
    }

    public static void enableRuntimeHighlight(UUID playerId, long gameTime) {
        runtimeHighlightExpiry.put(playerId, Long.MAX_VALUE);
        runtimeActivePlayers.add(playerId);
        runtimeExplicitlyDisabled.remove(playerId);
        LOGGER.info("[Highlight] Runtime enabled for player {}", playerId);
    }

    public static void disableRuntimeHighlight(UUID playerId) {
        runtimeHighlightExpiry.remove(playerId);
        runtimeActivePlayers.remove(playerId);
        runtimeExplicitlyDisabled.add(playerId);
        LOGGER.info("[Highlight] Runtime disabled for player {}", playerId);
    }

    public static int getRuntimeRemainingTicks(UUID playerId, long gameTime) {
        Long expiry = runtimeHighlightExpiry.get(playerId);
        if (expiry == null) return 0;
        long remaining = Math.max(0, expiry - gameTime);
        return remaining >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }

    public static void onServerTick(MinecraftServer server) {
        // Cleanup expired runtime highlights
        long gameTime = server.overworld().getGameTime();
        runtimeHighlightExpiry.entrySet().removeIf(e -> {
            if (gameTime >= e.getValue()) {
                runtimeActivePlayers.remove(e.getKey());
                LOGGER.info("[Highlight] Expired runtime for player {}", e.getKey());
                return true;
            }
            return false;
        });

        List<ServerPlayer> trackingPlayers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isEnabledFor(player.getUUID())) {
                trackingPlayers.add(player);
            }
        }

        long now = System.currentTimeMillis();
        int radius = CTConfig.scanRadius;

        // ================================================================
        // Locator tracking — runs EVERY TICK, independent of tracking state
        // ================================================================
        locatorTickCounter++;

        // Per-tick: check every player for locator possession
        Set<UUID> currentLocatorPlayers = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack locator = findLocator(player);
            if (locator.isEmpty()) continue;
            currentLocatorPlayers.add(player.getUUID());

            // Check for bound position change (network switch detection)
            if (!NetworkLocatorTool.isBound(locator)) {
                lastBindings.remove(player.getUUID());
                continue;
            }
            BlockPos boundPos = NetworkLocatorTool.getBoundPos(locator);
            ResourceLocation boundDimension = NetworkLocatorTool.getBoundDimension(locator);
            if (boundPos != null && boundDimension != null) {
                LocatorBinding binding = new LocatorBinding(boundDimension, boundPos);
                LocatorBinding previous = lastBindings.put(player.getUUID(), binding);
                if (!binding.equals(previous)) {
                    CraftTrackerNetwork.sendToPlayer(player, new S2CLocatorHighlights(Map.of(), 0));
                    if (player.level().dimension().location().equals(boundDimension)) {
                        performLocatorScan(player, locator, boundPos, gameTime);
                    }
                }
            }
        }

        // Per-tick: detect players who lost their locator (instant, no 40-tick delay)
        for (UUID uuid : playersWithLocatorLastTick) {
            if (!currentLocatorPlayers.contains(uuid)) {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                runtimeHighlightExpiry.remove(uuid);
                runtimeActivePlayers.remove(uuid);
                lastBindings.remove(uuid);
                if (player != null) {
                    CraftTrackerNetwork.sendToPlayer(player, new S2CLocatorHighlights(Map.of(), 0));
                    CraftTrackerNetwork.sendToPlayer(player, new S2CCraftHighlightData(List.of(), 0));
                } else {
                    LOGGER.warn("Player {} was in prevLoc set but is no longer online", uuid);
                }
            }
        }
        playersWithLocatorLastTick.clear();
        playersWithLocatorLastTick.addAll(currentLocatorPlayers);

        // Phase 4: Locator network scan — every 40 ticks (independent of tracking state)
        if (locatorTickCounter % 40 == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ItemStack locator = findLocator(player);
                if (locator.isEmpty()) continue;

                if (!NetworkLocatorTool.isBound(locator)) {
                    CraftTrackerNetwork.sendToPlayer(player, new S2CLocatorHighlights(Map.of(), 0));
                    continue;
                }

                ResourceLocation boundDim = NetworkLocatorTool.getBoundDimension(locator);
                if (boundDim == null || !player.level().dimension().location().equals(boundDim)) {
                    CraftTrackerNetwork.sendToPlayer(player, new S2CLocatorHighlights(Map.of(), 0));
                    continue;
                }

                BlockPos boundPos = NetworkLocatorTool.getBoundPos(locator);
                if (boundPos == null) {
                    CraftTrackerNetwork.sendToPlayer(player, new S2CLocatorHighlights(Map.of(), 0));
                    continue;
                }

                performLocatorScan(player, locator, boundPos, gameTime);
            }
        }

        // ================================================================
        // Above: always runs. Below: only when tracking is enabled.
        // ================================================================

        if (trackingPlayers.isEmpty()) {
            if (!entries.isEmpty()) {
                entries.clear();
            }
            return;
        }

        // Phase 1: every tick — refresh state for known entries + quick-check nearby for busy providers
        refreshEntries(server, now);
        quickScan(server, now, trackingPlayers);

        // Phase 2: periodic scan — discover providers and update state
        scanCounter++;
        boolean doScan = scanCounter % CTConfig.scanIntervalTicks == 0;

        if (doScan) {
            Set<ProviderKey> seen = new HashSet<>();
            Set<ProviderKey> seenProviders = new HashSet<>();

            for (ServerPlayer player : trackingPlayers) {
                ServerLevel level = player.serverLevel();
                BlockPos ppos = player.blockPosition();
                int chunkRadius = (int) Math.ceil(radius / 16.0);
                int cx0 = ppos.getX() >> 4;
                int cz0 = ppos.getZ() >> 4;

                scanChunks(level, ppos, cx0, cz0, chunkRadius, radius, now, seen, seenProviders);
            }

            // Clean up prevProviderBusy entries for providers no longer in range
            prevProviderBusy.keySet().removeIf(k -> !seenProviders.contains(k));

            // Keep entries still in cooldown or stuck
            for (Map.Entry<ProviderKey, TrackerEntry> e : entries.entrySet()) {
                if (now < e.getValue().cooldownUntilMs || e.getValue().stuck) {
                    seen.add(e.getKey());
                }
            }

            entries.entrySet().removeIf(e -> {
                if (!seen.contains(e.getKey())) {
                    e.getValue().missedCount++;
                    if (e.getValue().missedCount > MAX_MISSED) {
                        return true;
                    }
                }
                return false;
            });
        }

        // Phase 3: send highlights to each tracking player
        for (ServerPlayer player : trackingPlayers) {
            ServerLevel level = player.serverLevel();
            BlockPos ppos = player.blockPosition();
            List<HighlightEntry> highlightEntries = new ArrayList<>();

            for (Map.Entry<ProviderKey, TrackerEntry> e : entries.entrySet()) {
                if (!e.getKey().dimension().equals(level.dimension())) continue;
                BlockPos pos = e.getKey().pos();
                if (!pos.closerThan(ppos, radius)) continue;
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;

                CraftStatus status = computeStatus(e.getValue(), now);
                var outputs = e.getValue().outputs;
                // Skip entries with no known output unless stuck
                if ((outputs == null || outputs.isEmpty()) && !e.getValue().stuck) continue;
                List<HighlightEntry.OutputItem> packetOutputs = new ArrayList<>();
                if (outputs != null) {
                    for (OutputItem out : outputs) {
                        packetOutputs.add(new HighlightEntry.OutputItem(out.id(), out.type()));
                    }
                }
                highlightEntries.add(new HighlightEntry(pos, status.ordinal(), packetOutputs));
            }

            int runtimeRemaining = getRuntimeRemainingTicks(player.getUUID(), gameTime);
            CraftTrackerNetwork.sendToPlayer(player, new S2CCraftHighlightData(highlightEntries, runtimeRemaining));
        }
    }

    private static boolean hasAdjacentInventory(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (level.getBlockEntity(pos.relative(dir)) != null) return true;
        }
        return false;
    }

    private static void quickScan(MinecraftServer server, long now, List<ServerPlayer> trackingPlayers) {
        int quickRadius = Math.min(CTConfig.scanRadius, 24);
        int chunkRadius = (int) Math.ceil(quickRadius / 16.0);

        for (ServerPlayer player : trackingPlayers) {
            ServerLevel level = player.serverLevel();
            BlockPos ppos = player.blockPosition();
            int cx0 = ppos.getX() >> 4;
            int cz0 = ppos.getZ() >> 4;

            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                    int cx = cx0 + dx;
                    int cz = cz0 + dz;
                    if (!level.hasChunk(cx, cz)) continue;

                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (Map.Entry<BlockPos, BlockEntity> beEntry : chunk.getBlockEntities().entrySet()) {
                        BlockPos pos = beEntry.getKey();
                        BlockEntity be = beEntry.getValue();
                        if (!isPatternSource(be)) continue;
                        if (!pos.closerThan(ppos, quickRadius)) continue;

                        BlockPos immPos = pos.immutable();
                        ProviderKey providerKey = new ProviderKey(level.dimension(), immPos);

                        // Skip already tracked entries — refreshEntries handles them per-tick
                        if (entries.containsKey(providerKey)) continue;

                        boolean busy = isPatternBusy(be);
                        boolean locked = isPatternLocked(be);

                        if (busy || locked) {
                            TrackerEntry entry = new TrackerEntry(locked ? now : 0);
                            entry.stuck = locked;
                            entry.outputs = getOutputInfo(be);
                            entries.put(providerKey, entry);
                            prevProviderBusy.put(providerKey, true);
                        } else {
                            var info = getOutputInfo(be);
                            if (info != null) {
                                boolean hasInv = hasAdjacentInventory(level, immPos);
                                if (hasInv) {
                                    TrackerEntry entry = new TrackerEntry(0);
                                    entry.outputs = info;
                                    entry.tentative = true;
                                    entry.cooldownUntilMs = now + 1000;
                                    entries.put(providerKey, entry);
                                } else {
                                    TrackerEntry entry = new TrackerEntry(now);
                                    entry.outputs = info;
                                    entry.stuck = true;
                                    entry.lockStartMs = now;
                                    entries.put(providerKey, entry);
                                    prevProviderBusy.put(providerKey, false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void refreshEntries(MinecraftServer server, long now) {
        for (var e : entries.entrySet()) {
            ProviderKey providerKey = e.getKey();
            BlockPos pos = providerKey.pos();
            TrackerEntry entry = e.getValue();

            ServerLevel level = server.getLevel(providerKey.dimension());
            if (level == null || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (!isPatternSource(be)) continue;

                boolean busy = isPatternBusy(be);

                if (busy) {
                    entry.cooldownUntilMs = 0;
                    entry.missedCount = 0;
                    entry.tentative = false;
                    prevProviderBusy.put(providerKey, true);

                    boolean locked = isPatternLocked(be);

                    var info = getOutputInfo(be);
                    if (info != null) {
                        entry.outputs = info;
                    }

                    entry.stuck = locked;

                    // Track continuous busy time for output-full detection (busy but not locked)
                    if (!locked && entry.busyStartMs == 0) {
                        entry.busyStartMs = now;
                    }
                    if (locked && entry.lockStartMs == 0) {
                        entry.lockStartMs = now;
                    } else if (!locked) {
                        entry.lockStartMs = 0;
                    }
                } else {
                    entry.busyStartMs = 0;
                    if (entry.stuck) {
                        entry.missedCount = 0;
                    } else if (!entry.tentative) {
                        boolean cpuBusy = isGridCpuBusy(be);

                        if (cpuBusy) {
                            entry.missedCount = 0;
                            var info = getOutputInfo(be);
                            if (info != null) {
                                entry.outputs = info;
                            }
                            if (entry.cooldownUntilMs == 0 || entry.cooldownUntilMs - now < COOLDOWN_MS / 2) {
                                entry.cooldownUntilMs = now + COOLDOWN_MS;
                            }
                        } else if (now < entry.cooldownUntilMs) {
                            entry.missedCount = 0;
                            // CPU may have started a new job even if isGridCpuBusy was false
                            var info = getOutputInfo(be);
                            if (info != null) {
                                entry.outputs = info;
                            }
                        } else {
                            entry.lockStartMs = 0;
                        }
                    } else if (now < entry.cooldownUntilMs) {
                        entry.missedCount = 0;
                        var info = getOutputInfo(be);
                        if (info != null) {
                            entry.outputs = info;
                        }
                    } else {
                        entry.lockStartMs = 0;
                    }
                }
        }
    }

    private static void scanChunks(ServerLevel level, BlockPos ppos,
                                    int cx0, int cz0, int chunkRadius,
                                    int radius, long now, Set<ProviderKey> seen,
                                    Set<ProviderKey> seenProviders) {
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = cx0 + dx;
                int cz = cz0 + dz;
                if (!level.hasChunk(cx, cz)) continue;

                LevelChunk chunk = level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> beEntry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = beEntry.getKey();
                    BlockEntity be = beEntry.getValue();
                    if (!pos.closerThan(ppos, radius)) continue;
                    if (!isPatternSource(be)) continue;

                    BlockPos immPos = pos.immutable();
                    ProviderKey providerKey = new ProviderKey(level.dimension(), immPos);
                    seenProviders.add(providerKey);
                    boolean busy = isPatternBusy(be);
                    boolean locked = isPatternLocked(be);
                    boolean active = busy || locked;

                    boolean wasActive = prevProviderBusy.getOrDefault(providerKey, false);
                    prevProviderBusy.put(providerKey, active);

                    TrackerEntry existing = entries.get(providerKey);

                    if (active) {
                        seen.add(providerKey);

                        var info = getOutputInfo(be);

                        if (existing == null) {
                            TrackerEntry entry = new TrackerEntry(locked ? now : 0);
                            if (!locked) {
                                entry.busyStartMs = now;
                            }
                            entry.stuck = locked;
                            entry.outputs = info;
                            entries.put(providerKey, entry);
                        } else {
                            existing.missedCount = 0;
                            existing.cooldownUntilMs = 0;
                            existing.tentative = false;
                            existing.stuck = locked;
                            if (info != null) {
                                existing.outputs = info;
                            }
                            if (!locked && existing.busyStartMs == 0) {
                                existing.busyStartMs = now;
                            }
                            if (locked && existing.lockStartMs == 0) {
                                existing.lockStartMs = now;
                            } else if (!locked) {
                                existing.lockStartMs = 0;
                            }
                        }
                    } else {
                        // Provider is idle now
                        if (existing != null) {
                            existing.busyStartMs = 0;
                            if (existing.stuck) {
                                existing.missedCount = 0;
                                seen.add(providerKey);
                            } else if (wasActive) {
                                existing.cooldownUntilMs = now + COOLDOWN_MS;
                                existing.missedCount = 0;
                                seen.add(providerKey);
                            } else if (now < existing.cooldownUntilMs) {
                                // Still in cooldown — refresh item in case CPU switched jobs
                                var info = getOutputInfo(be);
                                if (info != null) {
                                    existing.outputs = info;
                                }
                                seen.add(providerKey);
                            }
                        } else if (wasActive) {
                            TrackerEntry entry = new TrackerEntry(0);
                            entry.outputs = getOutputInfo(be);
                            entry.cooldownUntilMs = now + COOLDOWN_MS;
                            entries.put(providerKey, entry);
                            seen.add(providerKey);
                        } else {
                            // Idle provider, no existing entry — check if pattern matches a busy CPU or is requested
                            var info = getOutputInfo(be);
                            if (info != null) {
                                TrackerEntry entry;
                                if (hasAdjacentInventory(level, immPos)) {
                                    entry = new TrackerEntry(0);
                                    entry.outputs = info;
                                    entry.cooldownUntilMs = now + COOLDOWN_MS;
                                } else {
                                    entry = new TrackerEntry(now);
                                    entry.outputs = info;
                                    entry.stuck = true;
                                    entry.lockStartMs = now;
                                }
                                entries.put(providerKey, entry);
                                seen.add(providerKey);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Find a bound NetworkLocatorTool in the player's inventory.
     * Checks main hand, offhand, then hotbar, then rest of inventory.
     */
    private static ItemStack findLocator(ServerPlayer player) {
        // Check main hand first (most common)
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof NetworkLocatorTool) return mainHand;
        // Check offhand
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof NetworkLocatorTool) return offHand;
        // Check rest of inventory
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof NetworkLocatorTool) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void performLocatorScan(ServerPlayer player, ItemStack locator, BlockPos boundPos, long gameTime) {
        List<ItemStack> filters = NetworkLocatorTool.getFilters(locator);
        if (filters.stream().allMatch(ItemStack::isEmpty)) return;

        SimpleContainer filterContainer = new SimpleContainer(9);
        for (int i = 0; i < Math.min(filters.size(), 9); i++) {
            filterContainer.setItem(i, filters.get(i));
        }

        Map<BlockPos, List<S2CLocatorHighlights.LocatorHit>> results =
                NetworkLocatorScanner.scan((ServerLevel) player.level(), boundPos, filterContainer);

        int runtimeRemaining = getRuntimeRemainingTicks(player.getUUID(), gameTime);
        CraftTrackerNetwork.sendToPlayer(player, new S2CLocatorHighlights(results, runtimeRemaining));
    }

    private static @Nullable List<OutputItem> getOutputInfo(BlockEntity be) {
        try {
            IGrid grid = getGrid(be);
            if (grid == null) return null;
            ICraftingService cs = grid.getCraftingService();
            if (cs == null) return null;

            var patterns = getPatterns(be);

            // Collect up to MAX_OUTPUTS matching items in pattern order to form a queue.
            // Items that match isCpuCraftingOutput OR isRequesting are included.
            // The queue naturally advances: when item finishes (no longer matches),
            // it drops out and remaining items shift forward.
            List<OutputItem> results = new ArrayList<>();
            for (IPatternDetails pattern : patterns) {
                if (results.size() >= MAX_OUTPUTS) break;
                GenericStack output = pattern.getPrimaryOutput();
                if (output == null) continue;
                AEKey key = output.what();
                if (isCpuCraftingOutput(cs, key) || cs.isRequesting(key)) {
                    OutputItem item = buildOutputItem(key);
                    if (item != null) {
                        results.add(item);
                    }
                }
            }
            return results.isEmpty() ? null : results;
        } catch (Exception e) {
            LOGGER.debug("Could not inspect Pattern Provider output at {}", be.getBlockPos(), e);
        }
        return null;
    }

    private static @Nullable OutputItem buildOutputItem(AEKey key) {
        ResourceLocation regKey = key.getId();
        if (key instanceof AEItemKey) {
            if (BuiltInRegistries.ITEM.containsKey(regKey)) {
                return new OutputItem(regKey, TYPE_ITEM);
            }
        } else if (key instanceof AEFluidKey) {
            if (BuiltInRegistries.FLUID.containsKey(regKey)) {
                return new OutputItem(regKey, TYPE_FLUID);
            }
        }
        return null;
    }

    private static boolean isGridCpuBusy(BlockEntity be) {
        if (be == null || be.getLevel() == null) return false;
        try {
            IGrid grid = getGrid(be);
            if (grid == null) return false;
            ICraftingService cs = grid.getCraftingService();
            if (cs == null) return false;
            for (ICraftingCPU cpu : cs.getCpus()) {
                if (cpu.isBusy()) return true;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not inspect crafting CPUs", e);
        }
        return false;
    }

    private static IGridNode getGridNode(BlockEntity be) {
        if (!(be instanceof IInWorldGridNodeHost host)) return null;
        IGridNode node = host.getGridNode(null);
        if (node != null) return node;
        for (var dir : Direction.values()) {
            node = host.getGridNode(dir);
            if (node != null) return node;
        }
        return null;
    }

    private static boolean isCpuCraftingOutput(ICraftingService cs, AEKey key) {
        try {
            for (ICraftingCPU cpu : cs.getCpus()) {
                if (!cpu.isBusy()) continue;
                CraftingJobStatus status = cpu.getJobStatus();
                if (status == null || status.crafting() == null) continue;
                AEKey cpuKey = status.crafting().what();
                if (cpuKey.equals(key) || cpuKey.getId().equals(key.getId())) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not inspect crafting CPU output", e);
        }
        return false;
    }

    private static CraftStatus computeStatus(TrackerEntry entry, long now) {
        if (entry.stuck) return CraftStatus.STUCK;

        long startMs;
        if (entry.lockStartMs != 0) {
            startMs = entry.lockStartMs;
        } else if (entry.busyStartMs != 0) {
            startMs = entry.busyStartMs;
        } else {
            return CraftStatus.ACTIVE;
        }

        long durationMs = now - startMs;
        long stuckMs = CTConfig.stuckThresholdSeconds * 1000L;
        long stallMs = CTConfig.stallThresholdSeconds * 1000L;

        if (durationMs >= stuckMs) return CraftStatus.STUCK;
        if (durationMs >= stallMs) return CraftStatus.STALLED;
        return CraftStatus.ACTIVE;
    }

    private static class TrackerEntry {
        long lockStartMs;
        long busyStartMs; // when busy+!locked started (output full detection), 0 = not busy
        int missedCount;
        long cooldownUntilMs;
        boolean tentative;
        boolean stuck;
        @Nullable List<OutputItem> outputs;

        TrackerEntry(long lockStartMs) {
            this.lockStartMs = lockStartMs;
        }
    }
}
