package me.voidxwalker.worldpreview;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import me.voidxwalker.worldpreview.mixin.access.ClientPlayNetworkHandlerAccessor;
import me.voidxwalker.worldpreview.mixin.access.EntityAccessor;
import me.voidxwalker.worldpreview.mixin.access.MinecraftClientAccessor;
import me.voidxwalker.worldpreview.mixin.access.PlayerEntityAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
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
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class WorldPreview {
    public static final boolean START_ON_OLD_WORLDS = false;

    public static final Object LOCK = new Object();
    public static final Logger LOGGER = LogManager.getLogger();
    public static final boolean HAS_STATEOUTPUT = FabricLoader.getInstance().isModLoaded("state-output");

    public static final ThreadLocal<Boolean> CALCULATING_SPAWN = new ThreadLocal<>();

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

    public static boolean configure(ServerWorld serverWorld) {
        WPFakeServerPlayerEntity fakePlayer;
        try {
            CALCULATING_SPAWN.set(true);
            fakePlayer = new WPFakeServerPlayerEntity(serverWorld.getServer(), serverWorld, MinecraftClient.getInstance().getSession().getProfile(), new ServerPlayerInteractionManager(serverWorld));
        } catch (WorldPreviewMissingChunkException e) {
            return false;
        } finally {
            CALCULATING_SPAWN.remove();
        }

        ClientPlayNetworkHandler networkHandler = new ClientPlayNetworkHandler(
                MinecraftClient.getInstance(),
                null,
                null,
                MinecraftClient.getInstance().getSession().getProfile()
        );
        ClientPlayerInteractionManager interactionManager = new ClientPlayerInteractionManager(
                MinecraftClient.getInstance(),
                networkHandler
        );

        ClientWorld world = new ClientWorld(
                networkHandler,
                new ClientWorld.Properties(serverWorld.getDifficulty(), serverWorld.getServer().isHardcore(), serverWorld.isFlat()),
                serverWorld.getRegistryKey(),
                serverWorld.getDimensionRegistryKey(),
                serverWorld.getDimension(),
                // WorldPreviews Chunk Distance is one lower than Minecraft's chunkLoadDistance,
                // when it's at 1 only the chunk the player is in gets sent
                config.chunkDistance - 1,
                MinecraftClient.getInstance()::getProfiler,
                WorldPreview.worldRenderer,
                serverWorld.isDebugWorld(),
                serverWorld.getSeed()
        );
        ClientPlayerEntity player = interactionManager.createPlayer(
                world,
                null,
                null
        );

        player.copyPositionAndRotation(fakePlayer);
        // avoid the ClientPlayer being removed from previews on id collisions by setting its entityId
        // to the ServerPlayer's as would be done in the ClientPlayNetworkHandler
        player.setEntityId(fakePlayer.getEntityId());
        // copy the inventory from the server player, for mods like icarus to render given items on preview
        player.inventory.deserialize(fakePlayer.inventory.serialize(new ListTag()));
        player.inventory.selectedSlot = fakePlayer.inventory.selectedSlot;
        // reset the randomness introduced to the yaw in LivingEntity#<init>
        player.headYaw = player.yaw = 0.0F;
        // the end result of the elytra lerp, is applied at the beginning because
        // otherwise seedqueue would have to take it into account when caching the framebuffer
        player.elytraPitch = (float) (Math.PI / 12);
        player.elytraRoll = (float) (-Math.PI / 12);

        GameMode gameMode = GameMode.NOT_SET;

        // This part is not actually relevant for previewing new worlds,
        // I just personally like the idea of worldpreview principally being able to work on old worlds as well
        // same with sending world info and scoreboard data
        CompoundTag playerData = serverWorld.getServer().getSaveProperties().getPlayerData();
        if (playerData != null) {
            player.fromTag(playerData);

            // see ServerPlayerEntity#readCustomDataFromTag
            if (playerData.contains("playerGameType", 99)) {
                gameMode = GameMode.byId(playerData.getInt("playerGameType"));
            }

            // see LivingEntity#readCustomDataFromTag, only gets read on server worlds
            if (playerData.contains("Attributes", 9)) {
                player.getAttributes().fromTag(playerData.getList("Attributes", 10));
            }

            // see PlayerManager#onPlayerConnect
            if (playerData.contains("RootVehicle", 10)) {
                CompoundTag vehicleData = playerData.getCompound("RootVehicle");
                UUID uUID = vehicleData.containsUuid("Attach") ? vehicleData.getUuid("Attach") : null;
                EntityType.loadEntityWithPassengers(vehicleData.getCompound("Entity"), serverWorld, entity -> {
                    entity.world = world;
                    world.addEntity(entity.getEntityId(), entity);
                    if (entity.getUuid().equals(uUID)) {
                        player.startRiding(entity, true);
                    }
                    return entity;
                });
            }
        }

        Camera camera = new Camera();

        Queue<Packet<?>> packetQueue = new LinkedBlockingQueue<>();
        packetQueue.add(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, fakePlayer));
        packetQueue.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, (gameMode != GameMode.NOT_SET ? gameMode : serverWorld.getServer().getDefaultGameMode()).getId()));

        // see PlayerManager#sendWorldInfo
        packetQueue.add(new WorldBorderS2CPacket(serverWorld.getWorldBorder(), WorldBorderS2CPacket.Type.INITIALIZE));
        packetQueue.add(new WorldTimeUpdateS2CPacket(serverWorld.getTime(), serverWorld.getTimeOfDay(), serverWorld.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE)));
        packetQueue.add(new PlayerSpawnPositionS2CPacket(serverWorld.getSpawnPos()));
        if (serverWorld.isRaining()) {
            packetQueue.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STARTED, 0.0f));
            packetQueue.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, serverWorld.getRainGradient(1.0f)));
            packetQueue.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED, serverWorld.getThunderGradient(1.0f)));
        }

        // see PlayerManager#sendScoreboard
        ServerScoreboard scoreboard = serverWorld.getScoreboard();
        HashSet<ScoreboardObjective> set = Sets.newHashSet();
        for (Team team : scoreboard.getTeams()) {
            packetQueue.add(new TeamS2CPacket(team, 0));
        }
        for (int i = 0; i < 19; ++i) {
            ScoreboardObjective scoreboardObjective = scoreboard.getObjectiveForSlot(i);
            if (scoreboardObjective == null || set.contains(scoreboardObjective)) {
                continue;
            }
            packetQueue.addAll(scoreboard.createChangePackets(scoreboardObjective));
            set.add(scoreboardObjective);
        }

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

        world.getChunkManager().setChunkMapCenter(player.chunkX, player.chunkZ);

        ((ClientPlayNetworkHandlerAccessor) player.networkHandler).setWorld(world);

        // camera has to be updated early for chunk/entity data culling to work
        int perspective = MinecraftClient.getInstance().options.perspective;
        camera.update(world, player, perspective > 0, perspective == 2, 0.0f);

        set(world, player, interactionManager, camera, packetQueue);
        return true;
    }

    public static void clear() {
        set(null, null, null, null, null);
    }

    public static boolean updateState() {
        if (inPreview) {
            return false;
        }
        synchronized (LOCK) {
            inPreview = world != null && player != null && interactionManager != null && camera != null && packetQueue != null;

            if (inPreview) {
                // we set the worldRenderer here instead of WorldPreview#configure because doing it from the server thread can cause issues
                worldRenderer.setWorld(world);
                logPreviewStart = true;
                kill = false;
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
