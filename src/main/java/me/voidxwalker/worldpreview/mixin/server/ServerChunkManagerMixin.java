package me.voidxwalker.worldpreview.mixin.server;

import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.ThreadedAnvilChunkStorageAccessor;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
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
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(ServerChunkManager.class)
public abstract class ServerChunkManagerMixin {

    @Shadow
    @Final
    private ServerWorld world;
    @Shadow
    @Final
    public ThreadedAnvilChunkStorage threadedAnvilChunkStorage;

    @Shadow
    @Nullable
    public abstract WorldChunk getWorldChunk(int chunkX, int chunkZ);

    @Unique
    private final Set<ChunkPos> sentChunks = new HashSet<>();
    @Unique
    private final Set<Integer> sentEntities = new HashSet<>();

    @Inject(method = "tick()Z", at = @At("RETURN"))
    private void getChunks(CallbackInfoReturnable<Boolean> cir) {
        if (this.world.getServer().getTicks() > 0) {
            return;
        }

        ClientWorld world;
        ClientPlayerEntity player;
        Set<Packet<?>> packetQueue;
        synchronized (WorldPreview.LOCK) {
            world = WorldPreview.world;
            player = WorldPreview.player;
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

            if (Math.abs(pos.x - player.chunkX) > 16 || Math.abs(pos.z - player.chunkZ) > 16) {
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

            Set<Packet<?>> chunkPackets = new LinkedHashSet<>();

            chunkPackets.add(new ChunkDataS2CPacket(chunk, 65535, true));
            chunkPackets.add(new LightUpdateS2CPacket(chunk.getPos(), chunk.getLightingProvider(), true));

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
                chunkPackets.add(new LightUpdateS2CPacket(neighbourChunk.getPos(), neighbourChunk.getLightingProvider(), false));
            }

            packetQueue.addAll(chunkPackets);

            this.sentChunks.add(pos);
        }

        ((ThreadedAnvilChunkStorageAccessor) this.threadedAnvilChunkStorage).getEntityTrackers().forEach((id, tracker) -> {
            if (this.sentEntities.contains(id)) {
                return;
            }

            ThreadedAnvilChunkStorageAccessor.EntityTrackerAccessor entityTracker = (ThreadedAnvilChunkStorageAccessor.EntityTrackerAccessor) tracker;
            EntityTrackerEntry entityTrackerEntry = entityTracker.getEntry();
            Entity entity = entityTracker.getEntity();

            if (!this.sentChunks.contains(new ChunkPos(entity.chunkX, entity.chunkZ))) {
                return;
            }

            Set<Packet<?>> entityPackets = new LinkedHashSet<>();

            entityTrackerEntry.sendPackets(entityPackets::add);
            entityPackets.add(new EntityS2CPacket.Rotate(id, (byte) MathHelper.floor(entity.yaw * 256.0f / 360.0f), (byte) MathHelper.floor(entity.pitch * 256.0f / 360.0f), entity.isOnGround()));
            entityPackets.add(new EntitySetHeadYawS2CPacket(entity, (byte) MathHelper.floor(entity.getHeadYaw() * 256.0f / 360.0f)));

            packetQueue.addAll(entityPackets);

            this.sentEntities.add(id);
        });
    }
}
