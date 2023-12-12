package me.voidxwalker.worldpreview.mixin.server;

import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.ClientChunkManagerAccessor;
import me.voidxwalker.worldpreview.mixin.access.ClientChunkMapAccessor;
import me.voidxwalker.worldpreview.mixin.access.ClientWorldAccessor;
import me.voidxwalker.worldpreview.mixin.access.ThreadedAnvilChunkStorageAccessor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {

    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Shadow
    public abstract @Nullable WorldChunk getWorldChunk(int chunkX, int chunkZ);

    @Inject(method = "tick()Z", at = @At("RETURN"))
    private void getChunks(CallbackInfoReturnable<Boolean> cir) {
        ClientWorld world;
        synchronized (WorldPreview.LOCK) {
            world = WorldPreview.world;
        }
        if (world == null) {
            return;
        }
        ClientChunkMapAccessor map = (ClientChunkMapAccessor) (Object) Objects.requireNonNull(((ClientChunkManagerAccessor) world.getChunkManager()).getChunks());
        for (ChunkHolder holder : ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage).getChunkHolders().values()) {
            if (holder == null) {
                // idk if this ever happens, but it was in the original WorldPreview, and I'd rather not break this
                continue;
            }
            ChunkPos pos = holder.getPos();
            int index = map.callGetIndex(pos.x, pos.z);
            if (map.callGetChunk(index) != null) {
                continue;
            }
            WorldChunk chunk = this.getWorldChunk(pos.x, pos.z);
            if (chunk == null) {
                continue;
            }
            map.callSet(index, chunk);
            world.resetChunkColor(pos.x, pos.z);
            for (TypeFilterableList<Entity> section : chunk.getEntitySectionArray()) {
                for (Entity entity : section.method_29903()) {
                    Entity copy = entity.getType().create(world);
                    if (copy == null) {
                        WorldPreview.LOGGER.warn("Failed to copy entity {}.", entity);
                        continue;
                    }
                    copy.setEntityId(entity.getEntityId());
                    copy.setUuid(entity.getUuid());
                    copy.copyPositionAndRotation(entity);
                    copy.setVelocity(entity.getVelocity());
                    // we can't use "world.addEntity(copy.getEntityId(), copy);" because it would add the entity to the chunk a second time
                    // this could be fixed by instead of directly putting the server chunks into the client chunkmap, making a client copy
                    // unsure how that would affect memory usage and performance tho
                    ((ClientWorldAccessor) world).getRegularEntities().put(copy.getEntityId(), copy);
                }
            }
        }
    }
}
