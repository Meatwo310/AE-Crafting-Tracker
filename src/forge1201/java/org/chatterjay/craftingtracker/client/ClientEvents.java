package org.chatterjay.craftingtracker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.craftingtracker.CraftingTracker;
import org.chatterjay.craftingtracker.config.CTConfig;
import org.chatterjay.craftingtracker.network.ModNetwork;

import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CraftingTracker.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {
    private ClientEvents() {}

    public static void bootstrap(IEventBus modBus) {
        modBus.addListener(ClientEvents::clientSetup);
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new ConfigScreen(parent)));
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(CraftingTracker.NETWORK_LOCATOR_MENU.get(), NetworkLocatorScreen::new));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.clear();
    }

    @SubscribeEvent
    public static void screenInit(ScreenEvent.Init.Post event) {
        String name = event.getScreen().getClass().getName();
        if (!name.endsWith("CraftingStatusScreen")) return;
        int x = event.getScreen().width / 2 + 42;
        int y = event.getScreen().height / 2 + 56;
        event.addListener(Button.builder(runtimeLabel(), button -> {
            boolean next = !ClientState.runtime();
            ClientState.setRuntimeLocal(next);
            ModNetwork.sendRuntimeToggle(next);
            button.setMessage(runtimeLabel());
        }).bounds(x, y, 58, 20).build());
    }

    private static Component runtimeLabel() {
        return Component.translatable(ClientState.runtime()
                ? "button.crafting_tracker.runtime_highlight.on"
                : "button.crafting_tracker.runtime_highlight.off");
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        PoseStack pose = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (var entry : ClientState.providers().values()) {
            int rgb = switch (entry.status()) {
                case 2 -> CTConfig.STUCK_COLOR.get();
                case 1 -> CTConfig.STALLED_COLOR.get();
                default -> CTConfig.ACTIVE_COLOR.get();
            };
            renderBox(pose, lines, entry.pos(), rgb, CTConfig.OUTLINE_ALPHA.get() / 255.0F);
        }
        for (BlockPos pos : ClientState.locator().keySet()) renderBox(pose, lines, pos, 0x55AAFF, 1.0F);
        pose.popPose();
        buffers.endBatch(RenderType.lines());

        for (var entry : ClientState.providers().values()) {
            renderIcons(mc, pose, camera, buffers, entry.pos(), entry.outputs(), false);
        }
        for (var entry : ClientState.locator().entrySet()) {
            renderIcons(mc, pose, camera, buffers, entry.getKey(), entry.getValue(), true);
        }
        buffers.endBatch();
    }

    private static void renderBox(PoseStack pose, VertexConsumer lines, BlockPos pos, int rgb, float alpha) {
        float r = ((rgb >> 16) & 255) / 255F;
        float g = ((rgb >> 8) & 255) / 255F;
        float b = (rgb & 255) / 255F;
        LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.004), r, g, b, alpha);
    }

    private static void renderIcons(Minecraft mc, PoseStack pose, Camera camera, MultiBufferSource.BufferSource buffers,
                                    BlockPos pos, List<ResourceLocation> ids, boolean showDistance) {
        int count = Math.min(3, ids.size());
        for (int i = 0; i < count; i++) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ids.get(i)));
            if (stack.isEmpty()) continue;
            pose.pushPose();
            Vec3 cam = camera.getPosition();
            pose.translate(pos.getX() + 0.5 - cam.x, pos.getY() + 1.35 - cam.y, pos.getZ() + 0.5 - cam.z);
            pose.mulPose(camera.rotation());
            pose.translate((i - (count - 1) / 2.0) * 0.38, 0, 0);
            pose.scale(0.35F, -0.35F, 0.35F);
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
            pose.popPose();
        }
        if (showDistance) {
            double distance = mc.player.position().distanceTo(Vec3.atCenterOf(pos));
            String text = String.format("%.0fm", distance);
            pose.pushPose();
            Vec3 cam = camera.getPosition();
            pose.translate(pos.getX() + 0.5 - cam.x, pos.getY() + 1.65 - cam.y, pos.getZ() + 0.5 - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(0.02F, -0.02F, 0.02F);
            float width = mc.font.width(text) / 2.0F;
            mc.font.drawInBatch(text, -width, 0, 0xFFFFFFFF, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, 0, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
    }
}
