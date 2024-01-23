package me.voidxwalker.worldpreview.mixin.server;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.worldpreview.WorldPreviewMissingChunkException;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpawnLocating.class)
public abstract class SpawnLocatingMixin {

    @WrapOperation(method = "findOverworldSpawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;"))
    private static WorldChunk doNotGenerateChunksDuringSpawnCalculation(ServerWorld world, int x, int z, Operation<WorldChunk> original) {
        if (((WPMinecraftServer) world.getServer()).worldpreview$isCalculatingSpawn()) {
            WorldChunk chunk = (WorldChunk) world.getExistingChunk(x, z);
            if (chunk == null) {
                throw new WorldPreviewMissingChunkException();
            }
            return chunk;
        }
        return original.call(world, x, z);
    }
}
