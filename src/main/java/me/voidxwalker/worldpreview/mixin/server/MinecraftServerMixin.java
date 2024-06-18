package me.voidxwalker.worldpreview.mixin.server;

import com.google.common.collect.Sets;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.voidxwalker.worldpreview.WPFakeServerPlayerEntity;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.WorldPreviewMissingChunkException;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.*;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.SaveProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.security.KeyPair;
import java.util.HashSet;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements WPMinecraftServer {

    @Unique
    protected volatile boolean killed;
    @Unique
    private volatile boolean tooLateToKill;
    @Unique
    private boolean shouldConfigurePreview;
    @Unique
    private boolean calculatingSpawn;

    @Shadow
    public abstract boolean isHardcore();

    @Shadow
    public abstract GameMode getDefaultGameMode();

    @Shadow
    public abstract SaveProperties getSaveProperties();

    @Shadow
    public abstract void setKeyPair(KeyPair keyPair);

    @ModifyExpressionValue(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerWorldProperties;isInitialized()Z"))
    private boolean setShouldConfigurePreview(boolean isInitialized) {
        this.shouldConfigurePreview = !isInitialized || WorldPreview.config.previewOldWorlds;
        return isInitialized;
    }

    @ModifyVariable(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I"))
    private ServerWorld configureWorldPreview(ServerWorld serverWorld) {
        if (this.shouldConfigurePreview) {
            long start = System.currentTimeMillis();

            WPFakeServerPlayerEntity fakePlayer;
            try {
                this.calculatingSpawn = true;
                fakePlayer = new WPFakeServerPlayerEntity(serverWorld.getServer(), serverWorld, MinecraftClient.getInstance().getSession().getProfile(), new ServerPlayerInteractionManager(serverWorld));
            } catch (WorldPreviewMissingChunkException e) {
                WorldPreview.debug("Waiting for more chunks to generate before calculating player spawn.");
                return serverWorld;
            } finally {
                this.calculatingSpawn = false;
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
                    new ClientWorld.Properties(serverWorld.getDifficulty(), this.isHardcore(), serverWorld.isFlat()),
                    serverWorld.getRegistryKey(),
                    serverWorld.getDimensionRegistryKey(),
                    serverWorld.getDimension(),
                    16,
                    MinecraftClient.getInstance()::getProfiler,
                    WorldPreview.worldRenderer,
                    serverWorld.isDebugWorld(),
                    serverWorld.getSeed()
            );
            ClientPlayerEntity player = interactionManager.method_29357(
                    world,
                    null,
                    null
            );

            player.copyPositionAndRotation(fakePlayer);

            GameMode gameMode = GameMode.NOT_SET;

            // This part is not actually relevant for previewing new worlds,
            // I just personally like the idea of worldpreview principally being able to work on old worlds as well
            // same with sending world info and scoreboard data
            CompoundTag playerData = this.getSaveProperties().getPlayerData();
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

            fakePlayer.interactionManager.setGameMode(gameMode != GameMode.NOT_SET ? gameMode : this.getDefaultGameMode(), GameMode.NOT_SET);

            Camera camera = new Camera();

            Queue<Packet<?>> packetQueue = new LinkedBlockingQueue<>();
            packetQueue.add(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, fakePlayer));
            packetQueue.add(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.GAME_MODE_CHANGED, fakePlayer.interactionManager.getGameMode().getId()));

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

            WorldPreview.configure(world, player, interactionManager, camera, packetQueue);

            this.shouldConfigurePreview = false;

            WorldPreview.debug("Took " + (System.currentTimeMillis() - start) + " ms to configure preview.");
        }
        return serverWorld;
    }

    @WrapOperation(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;addTicket(Lnet/minecraft/server/world/ChunkTicketType;Lnet/minecraft/util/math/ChunkPos;ILjava/lang/Object;)V"))
    private void captureChunkTicketInformation(ServerChunkManager chunkManager, ChunkTicketType<Object> ticketType, ChunkPos pos, int radius, Object argument, Operation<Void> original, @Share("removeTicket") LocalRef<Runnable> removeTicket) {
        removeTicket.set(() -> chunkManager.removeTicket(ticketType, pos, radius, argument));
        original.call(chunkManager, ticketType, pos, radius, argument);
    }

    @Inject(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I", shift = At.Shift.AFTER), cancellable = true)
    private void killWorldGen(WorldGenerationProgressListener worldGenerationProgressListener, CallbackInfo ci, @Share("removeTicket") LocalRef<Runnable> removeTicket) {
        if (this.killed) {
            removeTicket.get().run();
            worldGenerationProgressListener.stop();
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "shouldKeepTicking", at = @At("RETURN"))
    private boolean killRunningTasks(boolean shouldKeepTicking) {
        return shouldKeepTicking && !this.killed;
    }

    @ModifyExpressionValue(method = "runServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;setupServer()Z"))
    private synchronized boolean killServer(boolean original) {
        this.tooLateToKill = true;
        return original && !this.killed;
    }

    @Override
    public boolean worldpreview$isCalculatingSpawn() {
        return this.calculatingSpawn;
    }

    @Override
    public synchronized boolean worldpreview$kill() {
        if (this.tooLateToKill) {
            return false;
        }
        return this.killed = true;
    }
}
