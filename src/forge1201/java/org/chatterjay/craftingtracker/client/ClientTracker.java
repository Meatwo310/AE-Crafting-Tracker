package org.chatterjay.craftingtracker.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.chatterjay.craftingtracker.CraftingTracker;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CraftingTracker.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientTracker {
    private static final int SCAN_RADIUS_CHUNKS = 4;
    private static final long STALL_MS = 5_000L;
    private static final long STUCK_MS = 15_000L;
    private static final Map<BlockPos, Long> ACTIVE = new HashMap<>();
    private static int tick;

    private ClientTracker() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ACTIVE.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Map<BlockPos, Boolean> seen = new HashMap<>();
        int centerX = mc.player.chunkPosition().x;
        int centerZ = mc.player.chunkPosition().z;
        for (int dx = -SCAN_RADIUS_CHUNKS; dx <= SCAN_RADIUS_CHUNKS; dx++) {
            for (int dz = -SCAN_RADIUS_CHUNKS; dz <= SCAN_RADIUS_CHUNKS; dz++) {
                LevelChunk chunk = mc.level.getChunk(centerX + dx, centerZ + dz);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!looksLikePatternProvider(blockEntity)) continue;
                    BlockPos pos = blockEntity.getBlockPos().immutable();
                    seen.put(pos, Boolean.TRUE);
                    if (isBusy(blockEntity)) ACTIVE.putIfAbsent(pos, now);
                    else ACTIVE.remove(pos);
                }
            }
        }
        Iterator<BlockPos> iterator = ACTIVE.keySet().iterator();
        while (iterator.hasNext()) if (!seen.containsKey(iterator.next())) iterator.remove();
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        VertexConsumer lines = mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        long now = System.currentTimeMillis();

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (Map.Entry<BlockPos, Long> entry : ACTIVE.entrySet()) {
            long elapsed = now - entry.getValue();
            float r = elapsed >= STUCK_MS ? 1.0F : elapsed >= STALL_MS ? 1.0F : 0.1F;
            float g = elapsed >= STUCK_MS ? 0.1F : elapsed >= STALL_MS ? 0.85F : 1.0F;
            float b = 0.1F;
            AABB box = new AABB(entry.getKey()).inflate(0.004D);
            LevelRenderer.renderLineBox(poseStack, lines, box, r, g, b, 1.0F);
        }
        poseStack.popPose();
    }

    private static boolean looksLikePatternProvider(Object value) {
        String name = value.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("patternprovider") || name.contains("pattern_provider");
    }

    private static boolean isBusy(Object provider) {
        Boolean direct = invokeBoolean(provider, "isBusy");
        if (direct != null) return direct;
        for (String accessor : new String[]{"getLogic", "getPatternProviderLogic", "getMainNode"}) {
            Object nested = invoke(provider, accessor);
            Boolean nestedBusy = nested == null ? null : invokeBoolean(nested, "isBusy");
            if (nestedBusy != null) return nestedBusy;
        }
        return false;
    }

    private static Boolean invokeBoolean(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Boolean bool ? bool : null;
    }

    private static Object invoke(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
