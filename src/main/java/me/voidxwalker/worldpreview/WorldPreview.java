package me.voidxwalker.worldpreview;

import me.voidxwalker.worldpreview.mixin.access.ClientPlayNetworkHandlerAccessor;
import me.voidxwalker.worldpreview.mixin.access.PlayerEntityAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.Packet;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public class WorldPreview implements ClientModInitializer {

    public static final Object LOCK = new Object();
    public static final Logger LOGGER = LogManager.getLogger();

    public static WorldPreviewConfig config;

    public static ClientPlayerInteractionManager interactionManager;
    public static WorldRenderer worldRenderer;
    public static ClientPlayerEntity player;
    public static ClientWorld world;
    public static Camera camera;
    public static Set<Packet<?>> packetQueue;

    public static boolean inPreview;
    public static boolean renderingPreview;
    public static boolean logPreviewStart;
    public static boolean kill;

    public static KeyBinding resetKey;
    public static KeyBinding freezeKey;

    public static void debug(String message) {
        if (config.debug) {
            LOGGER.info("Worldpreview-DEBUG | " + message);
        }
    }

    @Override
    public void onInitializeClient() {
        resetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Leave Preview",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "key.categories.world_preview"
        ));

        freezeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Freeze Preview",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "key.categories.world_preview"
        ));
    }

    public static void set(ClientWorld world, ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, Camera camera, Set<Packet<?>> packetQueue) {
        synchronized (LOCK) {
            WorldPreview.world = world;
            WorldPreview.player = player;
            WorldPreview.interactionManager = interactionManager;
            WorldPreview.camera = camera;
            WorldPreview.packetQueue = packetQueue;
        }
    }

    public static void configure(ClientWorld world, ClientPlayerEntity player, ClientPlayerInteractionManager interactionManager, Camera camera, Set<Packet<?>> packetQueue) {
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
}
