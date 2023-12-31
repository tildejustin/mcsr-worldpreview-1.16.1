package me.voidxwalker.worldpreview.mixin.server;

import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.ThreadedAnvilChunkStorageAccessor;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {

    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Shadow
    @Nullable
    public abstract WorldChunk getWorldChunk(int chunkX, int chunkZ);

    @Unique
    private final Set<ChunkPos> sentChunks = new HashSet<>();

    @Inject(method = "tick()Z", at = @At("RETURN"))
    private void getChunks(CallbackInfoReturnable<Boolean> cir) {
        ClientWorld world;
        Set<Packet<?>> packetQueue;
        synchronized (WorldPreview.LOCK) {
            world = WorldPreview.world;
            packetQueue = WorldPreview.packetQueue;
        }
        if (world == null || packetQueue == null) {
            return;
        }

        for (ChunkHolder holder : ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage).getChunkHolders().values()) {
            if (holder == null) {
                continue;
            }

            ChunkPos pos = holder.getPos();
            if (this.sentChunks.contains(pos)) {
                continue;
            }

            WorldChunk chunk = this.getWorldChunk(pos.x, pos.z);
            if (chunk == null) {
                continue;
            }

            /*
            // instead of sending LightUpdateS2CPacket's to all the neighbour chunks we can also wait for all neighbours to be loaded before sending the ChunkDataS2CPacket
            // this would mean chunks being sent later in exchange for only having to evaluate lighting once clientside
            boolean allNeighboursLoaded = true;
            for (int i = 0; i < 9; i++) {
                allNeighboursLoaded &= this.isChunkLoaded(pos.x + (i % 3) - 1, pos.z + (i / 3) - 1);
            }
            if (!allNeighboursLoaded) {
                continue;
            }

             */

            packetQueue.add(new ChunkDataS2CPacket(chunk, 65535, true));
            packetQueue.add(new LightUpdateS2CPacket(chunk.getPos(), chunk.getLightingProvider(), true));

            for (int i = 0; i < 9; i++) {
                int xOffset = i % 3 - 1;
                int zOffset = i / 3 - 1;
                if (xOffset == 0 && zOffset == 0) {
                    continue;
                }
                ChunkPos neighbourChunkPos = new ChunkPos(pos.x + xOffset, pos.z + zOffset);
                if (!this.sentChunks.contains(neighbourChunkPos)) {
                    continue;
                }
                WorldChunk neighbourChunk = this.getWorldChunk(neighbourChunkPos.x, neighbourChunkPos.z);
                if (neighbourChunk == null) {
                    continue;
                }
                packetQueue.add(new LightUpdateS2CPacket(neighbourChunk.getPos(), neighbourChunk.getLightingProvider(), false));
            }

            for (TypeFilterableList<Entity> section : chunk.getEntitySectionArray()) {
                for (Entity entity : section.method_29903()) {
                    ((ThreadedAnvilChunkStorageAccessor.EntityTrackerAccessor) ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage).getEntityTrackers().get(entity.getEntityId())).getEntry().sendPackets(packetQueue::add);
                }
            }

            this.sentChunks.add(pos);
        }
    }
}
