package me.voidxwalker.worldpreview.mixin.parity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.WorldPreviewMissingChunkException;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.dimension.OverworldDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(OverworldDimension.class)
public abstract class SpawnLocatingMixin {

    @WrapOperation(
            method = "method_26525",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;"
            )
    )
    private static WorldChunk doNotGenerateChunksDuringSpawnCalculation(World world, int x, int z, Operation<WorldChunk> original) {
        if (Boolean.TRUE.equals(WorldPreview.CALCULATING_SPAWN.get())) {
            WorldChunk chunk = (WorldChunk) world.getExistingChunk(x, z);
            if (chunk == null) {
                throw WorldPreviewMissingChunkException.INSTANCE;
            }
            return chunk;
        }
        return original.call(world, x, z);
    }
}
