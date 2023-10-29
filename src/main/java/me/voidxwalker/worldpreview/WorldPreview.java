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
import net.minecraft.world.GameMode;
import org.apache.logging.log4j.Level;
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
    public static int kill = 0;
    public static boolean existingWorld;
    public static boolean showMenu;
    public static boolean calculatedSpawn;
    public static boolean freezePreview;

    public static KeyBinding resetKey;
    public static KeyBinding freezeKey;

    public static void log(Level level, String message) {
        LOGGER.log(level, message);
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

    public static boolean isPreview() {
        return inPreview;
    }
}
