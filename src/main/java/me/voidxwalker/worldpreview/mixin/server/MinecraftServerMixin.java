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
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements WPMinecraftServer {

    @Unique
    private volatile boolean killed;
    @Unique
    private volatile boolean tooLateToKill;
    @Unique
    private volatile boolean isNewWorld;

    @Shadow
    public abstract boolean isHardcore();

    @Shadow
    public abstract GameMode getDefaultGameMode();

    @Shadow
    public abstract SaveProperties getSaveProperties();

    @ModifyExpressionValue(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerWorldProperties;isInitialized()Z"))
    private boolean setIsNewWorld(boolean isInitialized) {
        this.isNewWorld = !isInitialized;
        return isInitialized;
    }

    @ModifyVariable(method = "prepareStartRegion", at = @At("STORE"))
    private ServerWorld configureWorldPreview(ServerWorld serverWorld) {
        if (this.isNewWorld) {
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

            WPFakeServerPlayerEntity fakePlayer = new WPFakeServerPlayerEntity((MinecraftServer) (Object) this, serverWorld, networkHandler.getProfile(), new ServerPlayerInteractionManager(serverWorld));

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
            player.bodyYaw = player.prevBodyYaw = fakePlayer.bodyYaw;
            player.headYaw = player.prevHeadYaw = fakePlayer.headYaw;

            GameMode gameMode = GameMode.NOT_SET;

            // This part is not actually relevant for previewing new worlds,
            // I just personally like the idea of worldpreview principally being able to work on old worlds as well
            // same with sending world info and scoreboard data
            CompoundTag playerData = this.getSaveProperties().getPlayerData();
            if (playerData != null) {
                player.fromTag(playerData);
                if (playerData.contains("playerGameType", 99)) {
                    gameMode = GameMode.byId(playerData.getInt("playerGameType"));
                }
            }

            fakePlayer.interactionManager.setGameMode(gameMode != GameMode.NOT_SET ? gameMode : this.getDefaultGameMode(), GameMode.NOT_SET);

            Camera camera = new Camera();

            Set<Packet<?>> packetQueue = Collections.synchronizedSet(new LinkedHashSet<>());
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
    public synchronized boolean worldpreview$kill() {
        if (this.tooLateToKill) {
            return false;
        }
        return this.killed = true;
    }
}
