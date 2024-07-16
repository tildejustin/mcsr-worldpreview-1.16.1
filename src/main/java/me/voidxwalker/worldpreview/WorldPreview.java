package me.voidxwalker.worldpreview;

import com.mojang.blaze3d.systems.RenderSystem;
import me.voidxwalker.worldpreview.mixin.access.ClientPlayNetworkHandlerAccessor;
import me.voidxwalker.worldpreview.mixin.access.EntityAccessor;
import me.voidxwalker.worldpreview.mixin.access.MinecraftClientAccessor;
import me.voidxwalker.worldpreview.mixin.access.PlayerEntityAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.MobSpawnS2CPacket;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Queue;

public class WorldPreview {
    public static final boolean START_ON_OLD_WORLDS = false;

    public static final Object LOCK = new Object();
    public static final Logger LOGGER = LogManager.getLogger();
    public static final boolean HAS_STATEOUTPUT = FabricLoader.getInstance().isModLoaded("state-output");

    public static WorldPreviewConfig config;

    public static ClientPlayerInteractionManager interactionManager;
    public static WorldRenderer worldRenderer;
    public static ClientPlayerEntity player;
    public static ClientWorld world;
    public static Camera camera;
    public static Queue<Packet<?>> packetQueue;

    public static boolean inPreview;
    public static boolean renderingPreview;
    public static boolean logPreviewStart;
    public static boolean kill;

    public static void set(ClientWorld world, ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, Camera camera, Queue<Packet<?>> packetQueue) {
        synchronized (LOCK) {
            WorldPreview.world = world;
            WorldPreview.player = player;
            WorldPreview.interactionManager = interactionManager;
            WorldPreview.camera = camera;
            WorldPreview.packetQueue = packetQueue;
        }
    }

    public static void configure(ClientWorld world, ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, Camera camera, Queue<Packet<?>> packetQueue) {
        synchronized (LOCK) {
            set(world, player, interactionManager, camera, packetQueue);

            // make player model parts visible
            int playerModelPartsBitMask = 0;
            for (PlayerModelPart playerModelPart : MinecraftClient.getInstance().options.getEnabledPlayerModelParts()) {
                playerModelPartsBitMask |= playerModelPart.getBitFlag();
            }
            player.getDataTracker().set(PlayerEntityAccessor.getPLAYER_MODEL_PARTS(), (byte) playerModelPartsBitMask);

            // set cape to player position
            player.prevCapeX = player.capeX = player.getX();
            player.prevCapeY = player.capeY = player.getY();
            player.prevCapeZ = player.capeZ = player.getZ();

            world.addPlayer(player.getEntityId(), player);

            // set player chunk coordinates,
            // usually these get set when adding the entity to a chunk,
            // however the chunk the player is in is not actually loaded yet
            player.chunkX = MathHelper.floor(player.getX() / 16.0);
            player.chunkY = MathHelper.clamp(MathHelper.floor(player.getY() / 16.0), 0, 16);
            player.chunkZ = MathHelper.floor(player.getZ() / 16.0);

            ((ClientPlayNetworkHandlerAccessor) player.networkHandler).setWorld(world);

            // camera has to be updated early for chunk/entity data culling to work
            int perspective = MinecraftClient.getInstance().options.perspective;
            camera.update(world, player, perspective > 0, perspective == 2, 0.0f);

            world.getChunkManager().setChunkMapCenter(player.chunkX, player.chunkZ);

            kill = false;
        }
    }

    public static void clear() {
        set(null, null, null, null, null);
    }

    public static boolean updateState() {
        if (WorldPreview.inPreview) {
            return false;
        }
        synchronized (WorldPreview.LOCK) {
            WorldPreview.inPreview = WorldPreview.world != null && WorldPreview.player != null && WorldPreview.interactionManager != null && WorldPreview.camera != null && WorldPreview.packetQueue != null;

            if (WorldPreview.inPreview) {
                // we set the worldRenderer here instead of WorldPreview#configure because doing it from the server thread can cause issues
                WorldPreview.worldRenderer.setWorld(WorldPreview.world);
                WorldPreview.logPreviewStart = true;
                return true;
            }
        }
        return false;
    }

    public static void runAsPreview(Runnable runnable) {
        MinecraftClient client = MinecraftClient.getInstance();

        WorldRenderer worldRenderer = client.worldRenderer;
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        Entity cameraEntity = client.cameraEntity;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;

        try {
            renderingPreview = true;

            ((MinecraftClientAccessor) client).setWorldRenderer(WorldPreview.worldRenderer);
            client.player = WorldPreview.player;
            client.world = WorldPreview.world;
            client.cameraEntity = WorldPreview.player;
            client.interactionManager = WorldPreview.interactionManager;

            runnable.run();
        } finally {
            renderingPreview = false;

            ((MinecraftClientAccessor) client).setWorldRenderer(worldRenderer);
            client.player = player;
            client.world = world;
            client.cameraEntity = cameraEntity;
            client.interactionManager = interactionManager;
        }
    }

    public static void tickPackets() {
        Profiler profiler = MinecraftClient.getInstance().getProfiler();
        int appliedPackets = 0;

        profiler.swap("tick_packets");
        while (!shouldStopAtPacket(packetQueue.peek(), appliedPackets)) {
            //noinspection unchecked
            Packet<ClientPlayPacketListener> packet = (Packet<ClientPlayPacketListener>) packetQueue.poll();
            profiler.push(packet.getClass().getSimpleName());
            packet.apply(player.networkHandler);
            appliedPackets++;
            profiler.pop();
        }
    }

    private static boolean shouldStopAtPacket(Packet<?> packet, int appliedPackets) {
        return packet == null || (config.dataLimit < 100 && config.dataLimit <= appliedPackets && canStopAtPacket(packet));
    }

    private static boolean canStopAtPacket(Packet<?> packet) {
        return packet instanceof ChunkDataS2CPacket || packet instanceof MobSpawnS2CPacket || packet instanceof EntitySpawnS2CPacket;
    }

    public static void tickEntities() {
        Profiler profiler = MinecraftClient.getInstance().getProfiler();

        profiler.swap("update_player_size");
        // clip the player into swimming/crawling mode if necessary
        ((PlayerEntityAccessor) player).callUpdateSize();

        profiler.swap("tick_new_entities");
        for (Entity entity : world.getEntities()) {
            if (!((EntityAccessor) entity).isFirstUpdate() || entity.getVehicle() != null && ((EntityAccessor) entity.getVehicle()).isFirstUpdate()) {
                continue;
            }
            tickEntity(entity);
            for (Entity passenger : entity.getPassengersDeep()) {
                tickEntity(passenger);
            }
        }
    }

    private static void tickEntity(Entity entity) {
        Profiler profiler = MinecraftClient.getInstance().getProfiler();
        profiler.push(() -> Registry.ENTITY_TYPE.getId(entity.getType()).toString());

        if (entity.getVehicle() != null) {
            entity.getVehicle().updatePassengerPosition(entity);
            entity.calculateDimensions();
            entity.updatePositionAndAngles(entity.getX(), entity.getY(), entity.getZ(), entity.yaw, entity.pitch);
        }
        entity.baseTick();

        profiler.pop();
    }

    @SuppressWarnings("deprecation")
    public static void render(MatrixStack matrices) {
        MinecraftClient client = MinecraftClient.getInstance();
        Profiler profiler = client.getProfiler();
        Window window = client.getWindow();

        profiler.swap("render_preview");

        RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.loadIdentity();
        RenderSystem.ortho(0.0, window.getFramebufferWidth(), window.getFramebufferHeight(), 0.0, 1000.0, 3000.0);
        RenderSystem.loadIdentity();
        RenderSystem.translatef(0.0F, 0.0F, 0.0F);
        DiffuseLighting.disableGuiDepthLighting();

        profiler.push("light_map");
        client.gameRenderer.getLightmapTextureManager().tick();
        profiler.swap("render_world");
        client.gameRenderer.renderWorld(0.0F, Util.getMeasuringTimeNano(), new MatrixStack());
        profiler.swap("entity_outlines");
        worldRenderer.drawEntityOutlinesFramebuffer();
        profiler.pop();

        RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.matrixMode(5889);
        RenderSystem.loadIdentity();
        RenderSystem.ortho(0.0D, (double) window.getFramebufferWidth() / window.getScaleFactor(), (double) window.getFramebufferHeight() / window.getScaleFactor(), 0.0D, 1000.0D, 3000.0D);
        RenderSystem.matrixMode(5888);
        RenderSystem.loadIdentity();
        RenderSystem.translatef(0.0F, 0.0F, -2000.0F);
        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.defaultAlphaFunc();

        profiler.push("ingame_hud");
        client.inGameHud.render(matrices, 0.0F);
        profiler.pop();

        RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
    }
}
