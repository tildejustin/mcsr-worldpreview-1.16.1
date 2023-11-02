package me.voidxwalker.worldpreview;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class WorldPreview implements ClientModInitializer {

    public static final Object LOCK = new Object();
    public static Logger LOGGER = LogManager.getLogger();

    public static final ClientPlayNetworkHandler DUMMY_NETWORK_HANDLER = new ClientPlayNetworkHandler(MinecraftClient.getInstance(), null, null, MinecraftClient.getInstance().getSession().getProfile());
    public static final ClientPlayerInteractionManager DUMMY_INTERACTION_MANAGER = new ClientPlayerInteractionManager(MinecraftClient.getInstance(), DUMMY_NETWORK_HANDLER);

    public static WorldRenderer worldRenderer;
    public static ClientPlayerEntity player;
    public static ClientWorld world;
    public static Camera camera;
    public static GameMode gameMode;

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

    public static void configure(ClientWorld world, ClientPlayerEntity player, Camera camera, GameMode gameMode) {
        synchronized (LOCK) {
            WorldPreview.world = world;
            WorldPreview.player = player;
            WorldPreview.camera = camera;
            WorldPreview.gameMode = gameMode;

            if (world != null && player != null) {
                player.chunkX = MathHelper.floor(player.getX() / 16.0);
                player.chunkY = MathHelper.clamp(MathHelper.floor(player.getY() / 16.0), 0, 16);
                player.chunkZ = MathHelper.floor(player.getZ() / 16.0);

                world.addPlayer(player.getEntityId(), player);
            }

            kill = false;
        }
    }

    public static void clear() {
        configure(null, null, null, null);
    }
}
