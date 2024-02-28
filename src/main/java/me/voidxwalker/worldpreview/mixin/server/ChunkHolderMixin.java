package me.voidxwalker.worldpreview.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import me.voidxwalker.worldpreview.interfaces.WPChunkHolder;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements WPChunkHolder {

    @Unique
    private int worldPreviewSkyLightUpdateBits;
    @Unique
    private int worldPreviewBlockLightUpdateBits;

    @Inject(method = "markForLightUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/WorldChunk;setShouldSave(Z)V", shift = At.Shift.AFTER))
    private void captureLightUpdates(LightType type, int y, CallbackInfo ci, @Local WorldChunk chunk) {
        if (type == LightType.SKY) {
            this.worldPreviewSkyLightUpdateBits |= 1 << y + 1;
        } else {
            this.worldPreviewBlockLightUpdateBits |= 1 << y + 1;
        }
    }

    @Override
    public int[] worldpreview$flushUpdates() {
        int[] lightUpdates = new int[]{
                this.worldPreviewSkyLightUpdateBits,
                this.worldPreviewBlockLightUpdateBits
        };
        this.worldPreviewSkyLightUpdateBits = 0;
        this.worldPreviewBlockLightUpdateBits = 0;
        return lightUpdates;
    }
}
