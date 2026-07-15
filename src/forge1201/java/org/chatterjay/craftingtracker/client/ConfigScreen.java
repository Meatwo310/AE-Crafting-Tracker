package org.chatterjay.craftingtracker.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.chatterjay.craftingtracker.config.CTConfig;

public final class ConfigScreen extends Screen {
    private final Screen parent;
    private Button enabled;
    private Button radius;
    private Button stall;
    private Button stuck;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("crafting_tracker.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 90;
        int y = height / 2 - 70;
        enabled = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CTConfig.HIGHLIGHT_ENABLED.set(!CTConfig.HIGHLIGHT_ENABLED.get());
            refresh();
        }).bounds(x, y, 180, 20).build());
        radius = addRenderableWidget(Button.builder(Component.empty(), b -> {
            int next = CTConfig.SCAN_RADIUS.get() >= 256 ? 16 : CTConfig.SCAN_RADIUS.get() + 16;
            CTConfig.SCAN_RADIUS.set(next);
            refresh();
        }).bounds(x, y + 24, 180, 20).build());
        stall = addRenderableWidget(Button.builder(Component.empty(), b -> {
            int next = CTConfig.STALL_SECONDS.get() >= 60 ? 1 : CTConfig.STALL_SECONDS.get() + 1;
            CTConfig.STALL_SECONDS.set(next);
            refresh();
        }).bounds(x, y + 48, 180, 20).build());
        stuck = addRenderableWidget(Button.builder(Component.empty(), b -> {
            int next = CTConfig.STUCK_SECONDS.get() >= 120 ? 5 : CTConfig.STUCK_SECONDS.get() + 5;
            CTConfig.STUCK_SECONDS.set(next);
            refresh();
        }).bounds(x, y + 72, 180, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x, y + 104, 180, 20).build());
        refresh();
    }

    private void refresh() {
        enabled.setMessage(Component.literal("Enabled by default: " + CTConfig.HIGHLIGHT_ENABLED.get()));
        radius.setMessage(Component.literal("Scan radius: " + CTConfig.SCAN_RADIUS.get()));
        stall.setMessage(Component.literal("Stall threshold: " + CTConfig.STALL_SECONDS.get() + "s"));
        stuck.setMessage(Component.literal("Stuck threshold: " + CTConfig.STUCK_SECONDS.get() + "s"));
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 95, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
