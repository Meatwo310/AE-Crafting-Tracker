package org.chatterjay.craftingtracker.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class CTConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue HIGHLIGHT_ENABLED;
    public static final ForgeConfigSpec.IntValue STALL_SECONDS;
    public static final ForgeConfigSpec.IntValue STUCK_SECONDS;
    public static final ForgeConfigSpec.IntValue SCAN_INTERVAL;
    public static final ForgeConfigSpec.IntValue SCAN_RADIUS;
    public static final ForgeConfigSpec.IntValue ACTIVE_COLOR;
    public static final ForgeConfigSpec.IntValue STALLED_COLOR;
    public static final ForgeConfigSpec.IntValue STUCK_COLOR;
    public static final ForgeConfigSpec.IntValue OUTLINE_ALPHA;

    static {
        BUILDER.push("highlight");
        HIGHLIGHT_ENABLED = BUILDER.comment("Enable Pattern Provider highlighting by default")
                .define("enabled", true);
        STALL_SECONDS = BUILDER.comment("Seconds before active becomes stalled")
                .defineInRange("stallThresholdSeconds", 5, 1, 60);
        STUCK_SECONDS = BUILDER.comment("Seconds before stalled becomes stuck")
                .defineInRange("stuckThresholdSeconds", 15, 5, 120);
        BUILDER.pop();

        BUILDER.push("scan");
        SCAN_INTERVAL = BUILDER.comment("Ticks between scans")
                .defineInRange("scanIntervalTicks", 10, 1, 100);
        SCAN_RADIUS = BUILDER.comment("Scan radius in blocks")
                .defineInRange("scanRadius", 64, 16, 256);
        BUILDER.pop();

        BUILDER.push("colors");
        ACTIVE_COLOR = BUILDER.defineInRange("active", 0x55FF55, 0, 0xFFFFFF);
        STALLED_COLOR = BUILDER.defineInRange("stalled", 0xFFFF55, 0, 0xFFFFFF);
        STUCK_COLOR = BUILDER.defineInRange("stuck", 0xFF5555, 0, 0xFFFFFF);
        OUTLINE_ALPHA = BUILDER.defineInRange("outlineAlpha", 255, 0, 255);
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private CTConfig() {}
}
