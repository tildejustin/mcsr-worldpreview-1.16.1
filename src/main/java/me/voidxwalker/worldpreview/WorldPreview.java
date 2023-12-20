package me.voidxwalker.worldpreview;

import me.voidxwalker.worldpreview.mixin.access.PlayerEntityAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.network.PlayerListEntry;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WorldPreview implements ClientModInitializer {

    public static final Object LOCK = new Object();
    public static Logger LOGGER = LogManager.getLogger();

    public static final ClientPlayNetworkHandler NETWORK_HANDLER = new ClientPlayNetworkHandler(MinecraftClient.getInstance(), null, null, MinecraftClient.getInstance().getSession().getProfile());
    public static final ClientPlayerInteractionManager INTERACTION_MANAGER = new ClientPlayerInteractionManager(MinecraftClient.getInstance(), NETWORK_HANDLER);

    public static WorldRenderer worldRenderer;
    public static ClientPlayerEntity player;
    public static ClientWorld world;
    public static Camera camera;
    public static PlayerListEntry playerListEntry;
    public static Set<Packet<?>> packetQueue;

    public static boolean inPreview;
    public static boolean renderingPreview;
    public static boolean kill;

    public static KeyBinding resetKey;
    public static KeyBinding freezeKey;

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

    public static void set(ClientWorld world, ClientPlayerEntity player, Camera camera, PlayerListEntry playerListEntry, Set<Packet<?>> packetQueue) {
        synchronized (LOCK) {
            WorldPreview.world = world;
            WorldPreview.player = player;
            WorldPreview.camera = camera;
            WorldPreview.playerListEntry = playerListEntry;
            WorldPreview.packetQueue = packetQueue;
        }
    }

    public static void configure(ClientWorld world, ClientPlayerEntity player, Camera camera, PlayerListEntry playerListEntry) {
        synchronized (LOCK) {
            set(world, player, camera, playerListEntry, Collections.synchronizedSet(new HashSet<>()));

            player.chunkX = MathHelper.floor(player.getX() / 16.0);
            player.chunkY = MathHelper.clamp(MathHelper.floor(player.getY() / 16.0), 0, 16);
            player.chunkZ = MathHelper.floor(player.getZ() / 16.0);

            int playerModelPartsBitMask = 0;
            for (PlayerModelPart playerModelPart : MinecraftClient.getInstance().options.getEnabledPlayerModelParts()) {
                playerModelPartsBitMask |= playerModelPart.getBitFlag();
            }
            player.getDataTracker().set(PlayerEntityAccessor.getPLAYER_MODEL_PARTS(), (byte) playerModelPartsBitMask);

            player.prevCapeX = player.capeX = player.getX();
            player.prevCapeY = player.capeY = player.getY();
            player.prevCapeZ = player.capeZ = player.getZ();

            world.addPlayer(player.getEntityId(), player);

            world.getChunkManager().setChunkMapCenter(player.chunkX, player.chunkZ);

            kill = false;
        }
    }

    public static void clear() {
        set(null, null, null, null, null);
    }
}
