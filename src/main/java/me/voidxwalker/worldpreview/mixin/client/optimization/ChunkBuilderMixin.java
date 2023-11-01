package me.voidxwalker.worldpreview.mixin.client.optimization;

import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Priority is set to 1500 so modifyBufferThreads is applied after sodiums MixinChunkBuilder#modifyThreadPoolSize
// sodium sets the variable to 0 to use its own system instead, for worldpreview however we need the vanilla worldrendering
// if rendering the worldpreview with sodium gets figured out, this mixin can be deleted (an equivalent mixin into sodium might be possible)
@Mixin(value = ChunkBuilder.class, priority = 1500)
public abstract class ChunkBuilderMixin {

    @Shadow
    @Final
    private WorldRenderer worldRenderer;

    // Override vanilla logic because 1 buffer thread is just better for multi instance, faster world gen and less memory usage (Author @jojoe77777)
    @ModifyVariable(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;newArrayListWithExpectedSize(I)Ljava/util/ArrayList;", remap = false), ordinal = 2)
    private int modifyBufferThreads(int threads) {
        if (this.isWorldPreview()) {
            System.out.println(threads);
            return 1;
        }
        return threads;
    }

    @Unique
    private boolean isWorldPreview() {
        return this.worldRenderer == WorldPreview.worldRenderer;
    }
}
