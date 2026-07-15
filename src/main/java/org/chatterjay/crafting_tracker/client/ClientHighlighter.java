package org.chatterjay.crafting_tracker.client;

import com.mojang.blaze3d.systems.RenderSystem;
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
import org.chatterjay.crafting_tracker.Crafting_tracker;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Crafting_tracker.MODID, value = Dist.CLIENT)
public final class ClientHighlighter {
    private static final Map<BlockPos, Status> HIGHLIGHTS = new HashMap<>();
    private static int tickCounter;

    private ClientHighlighter() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tickCounter % 20 != 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            HIGHLIGHTS.clear();
            return;
        }

        Map<BlockPos, Status> next = new HashMap<>();
        int centerX = minecraft.player.chunkPosition().x;
        int centerZ = minecraft.player.chunkPosition().z;
        for (int chunkX = centerX - 2; chunkX <= centerX + 2; chunkX++) {
            for (int chunkZ = centerZ - 2; chunkZ <= centerZ + 2; chunkZ++) {
                LevelChunk chunk = minecraft.level.getChunk(chunkX, chunkZ);
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    Status status = inspect(blockEntity);
                    if (status != null) {
                        next.put(blockEntity.getBlockPos().immutable(), status);
                    }
                }
            }
        }
        HIGHLIGHTS.clear();
        HIGHLIGHTS.putAll(next);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HIGHLIGHTS.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        RenderSystem.disableDepthTest();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        for (Map.Entry<BlockPos, Status> entry : HIGHLIGHTS.entrySet()) {
            BlockPos pos = entry.getKey();
            Status status = entry.getValue();
            AABB box = new AABB(pos).inflate(0.003D);
            LevelRenderer.renderLineBox(poseStack, consumer, box,
                    status.red, status.green, status.blue, 1.0F);
        }
        poseStack.popPose();
        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        RenderSystem.enableDepthTest();
    }

    private static Status inspect(BlockEntity blockEntity) {
        String name = blockEntity.getClass().getName().toLowerCase();
        if (!name.contains("patternprovider") && !name.contains("pattern_provider")) {
            return null;
        }

        Object target = invokeNoArg(blockEntity, "getLogic");
        if (target == null) {
            target = blockEntity;
        }

        Boolean busy = invokeBoolean(target, "isBusy");
        Boolean locked = invokeBoolean(target, "isLocked");
        if (!Boolean.TRUE.equals(busy) && !Boolean.TRUE.equals(locked)) {
            return null;
        }
        return Boolean.TRUE.equals(locked) ? Status.STUCK : Status.ACTIVE;
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = findMethod(target.getClass(), name);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Boolean invokeBoolean(Object target, String name) {
        Object result = invokeNoArg(target, name);
        return result instanceof Boolean value ? value : null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                return cursor.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        return null;
    }

    private enum Status {
        ACTIVE(0.33F, 1.0F, 0.33F),
        STUCK(1.0F, 0.33F, 0.33F);

        private final float red;
        private final float green;
        private final float blue;

        Status(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
    }
}
