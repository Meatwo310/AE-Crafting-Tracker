package org.chatterjay.crafting_tracker.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.capabilities.Capabilities;

import org.chatterjay.crafting_tracker.network.payloads.S2CLocatorHighlights.LocatorHit;
import org.slf4j.Logger;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;

import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NetworkLocatorScanner {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_CHEMICAL = 3;
    /** Max distinct icon slots per position */
    private static final int MAX_HITS_PER_POS = 3;

    /**
     * Scans the AE network at the bound position for all BlockEntity-hosted machines
     * whose inventory or patterns match items in the filter container.
     */
    public static Map<BlockPos, List<LocatorHit>> scan(ServerLevel level, BlockPos boundPos,
                                                       Container filterContainer, Player player) {
        Map<BlockPos, List<LocatorHit>> results = new HashMap<>();

        // Gather non-empty filter items
        List<ItemStack> filters = new ArrayList<>();
        for (int i = 0; i < filterContainer.getContainerSize(); i++) {
            ItemStack stack = filterContainer.getItem(i);
            if (!stack.isEmpty()) filters.add(stack);
        }
        if (filters.isEmpty()) return results;

        LOGGER.info("[LocatorScan] Scanning with {} filters for player {}", filters.size(), player.getName().getString());
        for (ItemStack f : filters) {
            LOGGER.info("[LocatorScan]   Filter: {} x{}", BuiltInRegistries.ITEM.getKey(f.getItem()), f.getCount());
        }

        // Get grid from bound position
        BlockEntity boundBe = level.getBlockEntity(boundPos);
        if (!(boundBe instanceof IInWorldGridNodeHost host)) {
            LOGGER.info("[LocatorScan] Bound position {} has no AE host", boundPos);
            return results;
        }

        IGrid grid = getGrid(host);
        if (grid == null) {
            LOGGER.info("[LocatorScan] No grid found at bound position {}", boundPos);
            return results;
        }
        LOGGER.info("[LocatorScan] Got grid, size={}, machine classes:", grid.size());
        for (var mc : grid.getMachineClasses()) {
            LOGGER.info("[LocatorScan]   Machine class: {}", mc.getName());
        }

        // Iterate ALL grid nodes and get BlockEntity owners
        Set<BlockPos> visited = new HashSet<>();
        int totalNodes = 0;
        int beNodes = 0;

        for (IGridNode node : grid.getNodes()) {
            totalNodes++;
            Object owner = node.getOwner();
            if (!(owner instanceof BlockEntity be)) continue;
            if (be.isRemoved()) continue;

            BlockPos pos = be.getBlockPos();
            if (!visited.add(pos)) continue; // deduplicate
            beNodes++;

            LOGGER.info("[LocatorScan]   #{} BE node at {}: {}",
                    beNodes, pos, be.getType().builtInRegistryHolder().key().location());

            List<LocatorHit> foundItems = new ArrayList<>();
            Set<ResourceLocation> foundTypes = new HashSet<>();

            // 1. Check IItemHandler capability (covers ME Interfaces, chests, etc.)
            checkItemHandler(be, filters, foundItems, foundTypes);

            // 2. Check pattern provider patterns (only if we have room)
            if (foundTypes.size() < MAX_HITS_PER_POS) {
                checkPatterns(be, filters, foundItems, foundTypes);
            }

            if (!foundItems.isEmpty()) {
                LOGGER.info("[LocatorScan]   >>> FOUND {} items at {}", foundItems.size(), pos);
                for (LocatorHit hit : foundItems) {
                    LOGGER.info("[LocatorScan]     Hit: {}", hit.itemId());
                }
                results.put(pos.immutable(), foundItems);
            } else {
                LOGGER.info("[LocatorScan]   No match at {}", pos);
            }
        }
        LOGGER.info("[LocatorScan] {} total nodes, {} BlockEntity owners", totalNodes, beNodes);

        if (!results.isEmpty()) {
            LOGGER.info("[LocatorScan] Scan complete: {} matches for {}", results.size(), player.getName().getString());
        }

        return results;
    }

    @Nullable
    private static IGrid getGrid(IInWorldGridNodeHost host) {
        IGridNode node = host.getGridNode(null);
        if (node != null) return node.getGrid();
        for (Direction dir : Direction.values()) {
            node = host.getGridNode(dir);
            if (node != null && node.getGrid() != null) return node.getGrid();
        }
        return null;
    }

    private static void checkItemHandler(BlockEntity be, List<ItemStack> filters, List<LocatorHit> foundItems, Set<ResourceLocation> foundTypes) {
        if (be.getLevel() == null) return;
        var cap = be.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
        if (cap == null) return;

        for (int i = 0; i < cap.getSlots() && foundTypes.size() < MAX_HITS_PER_POS; i++) {
            ItemStack slotStack = cap.getStackInSlot(i);
            if (slotStack.isEmpty()) continue;
            for (ItemStack filter : filters) {
                if (filter.getItem() == slotStack.getItem()) {
                    tryAddHit(foundItems, foundTypes, filter);
                    break;
                }
            }
        }
    }

    private static void checkPatterns(BlockEntity be, List<ItemStack> filters, List<LocatorHit> foundItems, Set<ResourceLocation> foundTypes) {
        List<IPatternDetails> patterns = getPatterns(be);
        if (patterns.isEmpty()) return;

        for (IPatternDetails pattern : patterns) {
            if (foundTypes.size() >= MAX_HITS_PER_POS) return;
            GenericStack output = pattern.getPrimaryOutput();
            if (output == null) continue;
            AEKey key = output.what();

            for (ItemStack filter : filters) {
                if (keyMatchesFilter(key, filter)) {
                    tryAddHit(foundItems, foundTypes, filter);
                    break;
                }
            }
        }
    }

    private static List<IPatternDetails> getPatterns(BlockEntity be) {
        if (be instanceof PatternProviderLogicHost host) return host.getLogic().getAvailablePatterns();
        if (be instanceof TileAssemblerMatrixPattern matrix) return matrix.getAvailablePatterns();
        if (be instanceof AdvPatternProviderLogicHost host) return host.getLogic().getAvailablePatterns();
        return List.of();
    }

    private static boolean keyMatchesFilter(AEKey key, ItemStack filter) {
        if (key instanceof AEItemKey itemKey) {
            return filter.getItem() == itemKey.getItem();
        }
        return false;
    }

    private static LocatorHit buildHit(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new LocatorHit(id, TYPE_ITEM);
    }

    /**
     * Adds a hit for the given filter item only if it hasn't been added yet
     * (deduplicates by item id) and we haven't reached MAX_HITS_PER_POS.
     */
    private static void tryAddHit(List<LocatorHit> foundItems, Set<ResourceLocation> foundTypes, ItemStack filter) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(filter.getItem());
        if (foundTypes.add(id)) {
            foundItems.add(buildHit(filter));
        }
    }
}
