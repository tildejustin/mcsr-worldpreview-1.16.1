package me.voidxwalker.worldpreview.mixin.client.render.chunk;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChunkBuilder.class)
public abstract class ChunkBuilderMixin {

    @Shadow @Final private WorldRenderer worldRenderer;

    @ModifyVariable(method = "<init>", at = @At("STORE"), ordinal = 2)
    private int modifyBufferThreads(int threads) {
        if (this.isWorldPreview()) {
            return 1;
        }
        return threads;
    }

    @Unique
    private boolean isWorldPreview() {
        return this.worldRenderer == WorldPreview.worldRenderer;
    }
}
