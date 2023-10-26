package me.voidxwalker.worldpreview.mixin.server;

import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.ClientChunkManagerAccessor;
import me.voidxwalker.worldpreview.mixin.access.ClientChunkMapAccessor;
import me.voidxwalker.worldpreview.mixin.access.ThreadedAnvilChunkStorageAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Shadow
    public abstract  @Nullable WorldChunk getWorldChunk(int chunkX, int chunkZ);

    @Inject(method = "tick()Z", at = @At(value = "TAIL"))
    private void worldpreview_getChunks(CallbackInfoReturnable<Boolean> cir) {
        synchronized (WorldPreview.lock) {
            if (WorldPreview.player != null && WorldPreview.calculatedSpawn && !WorldPreview.freezePreview && MinecraftClient.getInstance().currentScreen instanceof LevelLoadingScreen) {
                ClientChunkManager.ClientChunkMap map = ((((ClientChunkManagerAccessor) WorldPreview.clientWorld.getChunkManager()).getChunks()));
                Iterator<ChunkHolder> iterator = ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage).getChunkHolders().values().stream().iterator();
                while (iterator.hasNext()) {
                    ChunkHolder holder = iterator.next();
                    if (holder != null) {
                        int index = ((ClientChunkMapAccessor) (Object) (map)).callGetIndex(holder.getPos().x, holder.getPos().z);
                        if (((ClientChunkMapAccessor) (Object) (map)).callGetChunk(index) == null) {
                            WorldChunk chunk = this.getWorldChunk(holder.getPos().x, holder.getPos().z);
                            if (chunk != null) {
                                ((ClientChunkMapAccessor) (Object) (map)).callSet(index, chunk);
                            }
                        }
                    }
                }
            }
        }
    }
}
