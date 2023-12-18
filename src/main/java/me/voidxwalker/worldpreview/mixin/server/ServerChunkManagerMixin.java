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
/*

            // failed attempt at copying chunks instead of using the serverside ones for rendering (lighting was not working, especially bad with starlight)
            // getting this to work would remove the need for BlockEntityRenderDispatcherMixin#fixOffThreadGetBlockStateCalls

            ChunkDataS2CPacket packet = new ChunkDataS2CPacket(chunk, 65535, true);
            WorldChunk clientChunk = world.getChunkManager().loadChunkFromPacket(packet.getX(), packet.getZ(), packet.getBiomeArray(), packet.getReadBuffer(), packet.getHeightmaps(), packet.getVerticalStripBitmask(), packet.isFullChunk());
            if (clientChunk == null) {
                continue;
            }
            packet.getBlockEntityTagList().forEach(clientChunk::addPendingBlockEntityTag);

            LightingProvider lightingProvider;
            synchronized (lightingProvider = world.getLightingProvider()) {
                lightingProvider.setLightEnabled(clientChunk.getPos(), false);
                int verticalStripBitmask = packet.getVerticalStripBitmask();
                for (int l = -1; l < 17; ++l) {
                    if ((verticalStripBitmask & 1 << l) == 0) continue;
                    ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(clientChunk.getPos(), l);
                    lightingProvider.queueData(LightType.BLOCK, chunkSectionPos, lightingProvider.get(LightType.BLOCK).getLightArray(chunkSectionPos), false);
                    lightingProvider.queueData(LightType.SKY, chunkSectionPos, lightingProvider.get(LightType.SKY).getLightArray(chunkSectionPos), false);
                }
                lightingProvider.setLightEnabled(clientChunk.getPos(), true);
                clientChunk.getLightSourcesStream().forEach(blockPos -> lightingProvider.addLightSource(blockPos, clientChunk.getLuminance(blockPos)));
            }

 */

            for (TypeFilterableList<Entity> section : chunk.getEntitySectionArray()) {
                for (Entity entity : section.method_29903()) {
                    Entity copy = entity.getType().create(entity.world);
                    if (copy == null) {
                        WorldPreview.LOGGER.warn("Failed to copy entity {}.", entity);
                        continue;
                    }
                    copy.setEntityId(entity.getEntityId());
                    copy.setUuid(entity.getUuid());
                    copy.copyFrom(entity);
                    copy.copyPositionAndRotation(entity);
                    copy.setVelocity(entity.getVelocity());
                    copy.setWorld(world);

                    // we can't use "world.addEntity(copy.getEntityId(), copy);" because it would add the entity to the chunk a second time
                    // this could be fixed by instead of directly putting the server chunks into the client chunkmap, making a client copy
                    ((ClientWorldAccessor) world).getRegularEntities().put(copy.getEntityId(), copy);
                }
            }
        }
    }
}
