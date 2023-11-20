package me.voidxwalker.worldpreview.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.interfaces.FastCloseable;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import me.voidxwalker.worldpreview.mixin.access.SpawnLocatingAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.Random;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements WPMinecraftServer {

    @Unique
    private Integer spawnPos;
    @Unique
    private volatile boolean killed;
    @Unique
    private volatile boolean tooLateToKill;
    @Unique
    private volatile boolean isNewWorld;

    @Shadow
    public abstract int getSpawnRadius(@Nullable ServerWorld world);

    @Shadow
    public abstract boolean isHardcore();

    @Shadow
    public abstract GameMode getDefaultGameMode();

    @ModifyExpressionValue(method = "createWorlds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerWorldProperties;isInitialized()Z"))
    private boolean setIsNewWorld(boolean isInitialized) {
        this.isNewWorld = !isInitialized;
        return isInitialized;
    }

    @ModifyVariable(method = "prepareStartRegion", at = @At("STORE"))
    private ServerWorld configureWorldPreview(ServerWorld serverWorld) {
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
            Camera camera = new Camera();
            PlayerListEntry playerListEntry = new PlayerListEntry(
                    new PlayerListS2CPacket().new Entry(
                            WorldPreview.DUMMY_NETWORK_HANDLER.getProfile(),
                            0,
                            this.getDefaultGameMode(),
                            player.getDisplayName()
                    )
            );

            this.spawnPos = this.worldpreview$calculateSpawn(serverWorld, player);

            WorldPreview.configure(world, player, camera, playerListEntry);
        }
        return serverWorld;
    }

    @Inject(method = "prepareStartRegion", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerChunkManager;getTotalChunksLoadedCount()I", shift = At.Shift.AFTER), cancellable = true)
    private void killWorldGen(CallbackInfo ci) {
        if (this.killed) {
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

    @ModifyExpressionValue(method = "shutdown", at = @At(value = "FIELD", target = "Lnet/minecraft/server/MinecraftServer;playerManager:Lnet/minecraft/server/PlayerManager;", ordinal = 0))
    private PlayerManager killPlayerSave(PlayerManager playerManager) {
        if (this.killed) {
            return null;
        }
        return playerManager;
    }

    @WrapOperation(method = "shutdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;close()V"))
    private void killServerWorldClose(ServerWorld serverWorld, Operation<Void> original) throws IOException {
        if (this.killed) {
            ((FastCloseable) serverWorld.getChunkManager().threadedAnvilChunkStorage).worldpreview$fastClose();
            return;
        }
        original.call(serverWorld);
    }

    @WrapWithCondition(method = "shutdown", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;save(ZZZ)Z"))
    private boolean killSave(MinecraftServer server, boolean bl, boolean bl2, boolean bl3) {
        return !this.killed;
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

            while (!world.doesNotCollide(player) && player.getY() < 255.0) {
                player.updatePosition(player.getX(), player.getY() + 1.0, player.getZ());
            }
        }
        return null;
    }

    @Override
    public Integer worldpreview$getSpawnPos() {
        Integer spawnPos = this.spawnPos;
        this.spawnPos = null;
        return spawnPos;
    }

    @Override
    public synchronized boolean worldpreview$kill() {
        if (this.tooLateToKill) {
            return false;
        }
        this.killed = true;
        return true;
    }
}
