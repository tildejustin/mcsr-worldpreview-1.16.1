package me.voidxwalker.worldpreview.mixin.server;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Shadow
    @Final
    public MinecraftServer server;

    @ModifyExpressionValue(method = "moveToSpawn", at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I"))
    private int setSpawnPos(int original) {
        Integer spawnPos = ((WPMinecraftServer) this.server).worldpreview$getAndResetSpawnPos();
        if (spawnPos != null) {
            return spawnPos;
        }
        return original;
    }
}