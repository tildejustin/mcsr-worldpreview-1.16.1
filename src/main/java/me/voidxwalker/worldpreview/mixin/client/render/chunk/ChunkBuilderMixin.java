package me.voidxwalker.worldpreview.mixin.client.render.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.world.World;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.Executor;

@Mixin(ChunkBuilder.class)
public class ChunkBuilderMixin {
    @Shadow
    @Final
    private static Logger LOGGER;

    @Mutable
    @Shadow
    @Final
    private Queue<BlockBufferBuilderStorage> threadBuffers;

    @Shadow
    private volatile int bufferCount;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    public void worldpreview_sodiumCompatibility(World world, WorldRenderer worldRenderer, Executor executor, boolean is64Bits, BlockBufferBuilderStorage buffers, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof LevelLoadingScreen) {
            // Override vanilla logic because 1 buffer thread is just better for multi instance, faster world gen and less memory usage (Author @jojoe77777)
            int l = 1;
            ArrayList<BlockBufferBuilderStorage> list = this.worldpreview_getList(l);
            try {
                for (int m = 0; m < l; ++m) {
                    list.add(new BlockBufferBuilderStorage());
                }
            } catch (OutOfMemoryError var14) {
                LOGGER.warn("Allocated only {}/{} buffers", list.size(), l);
                int n = Math.min(list.size() * 2 / 3, list.size() - 1);

                for (int o = 0; o < n; ++o) {
                    list.remove(list.size() - 1);
                }
            }

            this.threadBuffers = Queues.newArrayDeque(list);
            this.bufferCount = this.threadBuffers.size();
        }
    }

    @Unique
    private ArrayList<BlockBufferBuilderStorage> worldpreview_getList(int l) {
        return Lists.newArrayListWithExpectedSize(l);
    }
}
