package me.voidxwalker.worldpreview.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.interfaces.FastCloseable;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import me.voidxwalker.worldpreview.mixin.access.MinecraftClientAccessor;
import me.voidxwalker.worldpreview.mixin.access.SpawnLocatingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ServerResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerNetworkIo;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.snooper.Snooper;
import net.minecraft.world.GameMode;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.LockSupport;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements WPMinecraftServer {

    @Shadow
    @Final
    private static Logger LOGGER;
    @Shadow
    @Final
    protected LevelStorage.Session session;
    @Shadow
    @Final
    private Snooper snooper;
    @Shadow
    private ServerResourceManager serverResourceManager;
    @Shadow
    private volatile boolean loading;

    @Unique
    private Integer spawnPos;
    @Unique
    private boolean killed;
    @Unique
    private boolean isNewWorld;


    @Shadow
    public abstract Iterable<ServerWorld> getWorlds();

    @Shadow
    public abstract @Nullable ServerNetworkIo getNetworkIo();

    @Shadow
    public abstract int getSpawnRadius(@Nullable ServerWorld world);

    @Shadow public abstract boolean isHardcore();

    @Shadow public abstract GameMode getDefaultGameMode();

    @ModifyVariable(method = "prepareStartRegion", at = @At("STORE"))
    private ServerWorld worldpreview_getWorld(ServerWorld serverWorld) {
        if (this.isNewWorld) {
            ClientWorld world = new ClientWorld(
                    WorldPreview.DUMMY_NETWORK_HANDLER,
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
            ClientPlayerEntity player = new ClientPlayerEntity(
                    MinecraftClient.getInstance(),
                    world,
                    WorldPreview.DUMMY_NETWORK_HANDLER,
                    null,
                    null,
                    false,
                    false
            );

            this.spawnPos = this.worldpreview$calculateSpawn(serverWorld, player);

            WorldPreview.configure(world, player, new Camera(), this.getDefaultGameMode());
        }
        return serverWorld;
    }

    /**
     * Copied from ServerPlayerEntity#moveToSpawn.
     */
    @SuppressWarnings("all")
    @Unique
    private @Nullable Integer worldpreview$calculateSpawn(ServerWorld world, PlayerEntity player) {
        BlockPos blockPos = world.getSpawnPos();
        if (world.getDimension().hasSkyLight() && world.getServer().getSaveProperties().getGameMode() != GameMode.ADVENTURE) {
            int i = Math.max(0, this.getSpawnRadius(world));
            int j = MathHelper.floor(world.getWorldBorder().getDistanceInsideBorder((double)blockPos.getX(), (double)blockPos.getZ()));
            if (j < i) {
                i = j;
            }

            if (j <= 1) {
                i = 1;
            }

            long l = (long)(i * 2 + 1);
            long m = l * l;
            int k = m > 2147483647L ? Integer.MAX_VALUE : (int)m;
            int n = k <= 16 ? k - 1 : 17;;
            int o = (new Random()).nextInt(k);

            for (int p = 0; p < k; ++p) {
                int q = (o + n * p) % k;
                int r = q % (i * 2 + 1);
                int s = q / (i * 2 + 1);
                BlockPos blockPos2 = SpawnLocatingAccessor.callFindOverworldSpawn(world, blockPos.getX() + r - i, blockPos.getZ() + s - i, false);
                if (blockPos2 != null) {
                    player.refreshPositionAndAngles(blockPos2, 0.0F, 0.0F);
                    if (world.doesNotCollide(player)) {
                        break;
                    }
                }
            }
            return o;
        } else {
            player.refreshPositionAndAngles(blockPos, 0.0F, 0.0F);

            while(!world.doesNotCollide(player) && player.getY() < 255.0) {
                player.updatePosition(player.getX(), player.getY() + 1.0, player.getZ());
            }
        }
        return null;
    }

    @Inject(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I", shift = At.Shift.AFTER), cancellable = true)
    private void killWorldGen(CallbackInfo ci) {
        if (this.killed) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "runServer", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;running:Z"))
    private boolean killServer(boolean original) {
        if (!this.loading && this.killed) {
            return false;
        }
        return original;
    }

    @Inject(method = "shutdown", at = @At("HEAD"), cancellable = true)
    private void shutdownWithoutSave(CallbackInfo ci) {
        if (this.killed) {
            LOGGER.info("Stopping server");
            if (this.getNetworkIo() != null) {
                this.getNetworkIo().stop();
            }
            for (ServerWorld serverWorld : this.getWorlds()) {
                if (serverWorld != null) {
                    serverWorld.savingDisabled = false;
                }
            }
            for (ServerWorld serverWorld : this.getWorlds()) {
                if (serverWorld != null) {
                    try {
                        ((FastCloseable) serverWorld.getChunkManager().threadedAnvilChunkStorage).worldpreview$fastClose();
                    } catch (IOException ignored) {
                    }
                }
            }
            if (this.snooper.isActive()) {
                this.snooper.cancel();
            }
            this.serverResourceManager.close();
            try {
                this.session.close();
            } catch (IOException var4) {
                LOGGER.error("Failed to unlock level {}", this.session.getDirectoryName(), var4);
            }
            ci.cancel();
        }
    }

    @Override
    public Integer worldpreview$getSpawnPos() {
        Integer spawnPos = this.spawnPos;
        this.spawnPos = null;
        return spawnPos;
    }

    @Override
    public void worldpreview$kill() {
        this.killed = true;
    }

    @Override
    public void worldpreview$setIsNewWorld(boolean isNewWorld) {
        this.isNewWorld = isNewWorld;
    }
}
