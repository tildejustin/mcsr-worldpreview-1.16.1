package me.voidxwalker.worldpreview.mixin.server;

import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.ClientChunkManagerAccessor;
import me.voidxwalker.worldpreview.mixin.access.ClientChunkMapAccessor;
import me.voidxwalker.worldpreview.mixin.access.ThreadedAnvilChunkStorageAccessor;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.Objects;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {
    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Shadow
    public abstract  @Nullable WorldChunk getWorldChunk(int chunkX, int chunkZ);

    @Inject(method = "tick()Z", at = @At("TAIL"))
    private void worldpreview_getChunks(CallbackInfoReturnable<Boolean> cir) {
        synchronized (WorldPreview.LOCK) {
            if (WorldPreview.isPreview() && WorldPreview.world != null) {
                ClientChunkMapAccessor map = (ClientChunkMapAccessor) (Object) Objects.requireNonNull(((ClientChunkManagerAccessor) WorldPreview.world.getChunkManager()).getChunks());
                for (ChunkHolder holder : ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage).getChunkHolders().values()) {
                    if (holder == null) {
                        continue;
                    }
                    ChunkPos pos = holder.getPos();
                    int index = map.callGetIndex(pos.x, pos.z);
                    if (map.callGetChunk(index) == null) {
                        WorldChunk chunk = this.getWorldChunk(pos.x, pos.z);
                        if (chunk != null) {
                            map.callSet(index, chunk);
                        }
                    }
                }
            }
        }
    }
}
