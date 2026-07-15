package org.chatterjay.craftingtracker;

import net.minecraftforge.fml.common.Mod;

@Mod(CraftingTracker.MOD_ID)
public final class CraftingTracker {
    public static final String MOD_ID = "crafting_tracker";

    public CraftingTracker() {
        // Client functionality is registered through the Forge event-bus subscriber.
    }
}
