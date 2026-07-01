package org.chatterjay.crafting_tracker.client;

import net.minecraft.core.BlockPos;

import org.chatterjay.crafting_tracker.network.payloads.S2CLocatorHighlights;
import org.chatterjay.crafting_tracker.network.payloads.S2CLocatorHighlights.LocatorHit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum ClientLocatorCache {
    INSTANCE;

    private final Map<BlockPos, List<LocatorHit>> hits = new ConcurrentHashMap<>();

    public void update(S2CLocatorHighlights data) {
        hits.clear();
        hits.putAll(data.hits());
    }

    public Map<BlockPos, List<LocatorHit>> getActiveHits() {
        return Map.copyOf(hits);
    }

    public void clear() {
        hits.clear();
    }
}
