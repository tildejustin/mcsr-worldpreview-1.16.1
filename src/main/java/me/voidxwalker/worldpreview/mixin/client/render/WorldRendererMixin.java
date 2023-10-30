package me.voidxwalker.worldpreview.mixin.client.render;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.BuiltChunkStorageAccessor;
import me.voidxwalker.worldpreview.mixin.access.ChunkInfoAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Shadow
    @Final
    public static Direction[] DIRECTIONS;

    @Shadow
    private ClientWorld world;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private BuiltChunkStorage chunks;

    @Shadow
    private ChunkBuilder chunkBuilder;

    @Shadow
    @Final
    private ObjectList<WorldRenderer.ChunkInfo> visibleChunks;

    @Shadow
    private int renderDistance;

    @Shadow
    private double lastCameraChunkUpdateX;

    @Shadow
    private double lastCameraChunkUpdateY;

    @Shadow
    private double lastCameraChunkUpdateZ;

    @Shadow
    private int cameraChunkX;

    @Shadow
    private int cameraChunkY;

    @Shadow
    private int cameraChunkZ;

    @Shadow
    private boolean needsTerrainUpdate;

    @Shadow
    private double lastCameraX;

    @Shadow
    private Set<ChunkBuilder.BuiltChunk> chunksToRebuild;

    @Shadow
    private double lastCameraPitch;

    @Shadow
    private double lastCameraY;

    @Shadow
    private double lastCameraYaw;

    @Shadow
    private double lastCameraZ;

    @Shadow
    public abstract void reload();

    @Shadow
    @Nullable
    protected abstract ChunkBuilder.BuiltChunk getAdjacentChunk(BlockPos pos, ChunkBuilder.BuiltChunk chunk, Direction direction);

    @Inject(method = "reload", at = @At("TAIL"))
    private void worldpreview_reload(CallbackInfo ci) {
        if (this.world != null && this.isWorldPreview()) {
            this.chunks = new BuiltChunkStorage(this.chunkBuilder, this.world, this.client.options.viewDistance, (WorldRenderer) (Object) this);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZIZ)V"))
    private void worldpreview_setupTerrain(WorldRenderer worldRenderer, Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, Operation<Void> original) {
        if (!this.isWorldPreview()) {
            original.call(worldRenderer, camera, frustum, hasForcedFrustum, frame, spectator);
            return;
        }
        Vec3d vec3d = camera.getPos();
        if (this.client.options.viewDistance != this.renderDistance) {
            this.reload();
        }

        this.world.getProfiler().push("camera");
        double d = WorldPreview.player.getX() - this.lastCameraChunkUpdateX;
        double e = WorldPreview.player.getY() - this.lastCameraChunkUpdateY;
        double f = WorldPreview.player.getZ() - this.lastCameraChunkUpdateZ;
        if (this.cameraChunkX != WorldPreview.player.chunkX
                || this.cameraChunkY != WorldPreview.player.chunkY
                || this.cameraChunkZ != WorldPreview.player.chunkZ
                || d * d + e * e + f * f > 16.0) {
            this.lastCameraChunkUpdateX = WorldPreview.player.getX();
            this.lastCameraChunkUpdateY = WorldPreview.player.getY();
            this.lastCameraChunkUpdateZ = WorldPreview.player.getZ();
            this.cameraChunkX = WorldPreview.player.chunkX;
            this.cameraChunkY = WorldPreview.player.chunkY;
            this.cameraChunkZ = WorldPreview.player.chunkZ;
            this.chunks.updateCameraPosition(WorldPreview.player.getX(), WorldPreview.player.getZ());
        }

        this.chunkBuilder.setCameraPosition(vec3d);
        this.world.getProfiler().swap("cull");
        this.client.getProfiler().swap("culling");
        BlockPos blockPos = camera.getBlockPos();
        ChunkBuilder.BuiltChunk builtChunk = ((BuiltChunkStorageAccessor) this.chunks).callGetRenderedChunk(blockPos);
        BlockPos blockPos2 = new BlockPos(MathHelper.floor(vec3d.x / 16.0) * 16, MathHelper.floor(vec3d.y / 16.0) * 16, MathHelper.floor(vec3d.z / 16.0) * 16);
        float g = camera.getPitch();
        float h = camera.getYaw();
        this.needsTerrainUpdate = this.needsTerrainUpdate
                || !this.chunksToRebuild.isEmpty()
                || vec3d.x != this.lastCameraX
                || vec3d.y != this.lastCameraY
                || vec3d.z != this.lastCameraZ
                || (double) g != this.lastCameraPitch
                || (double) h != this.lastCameraYaw;
        this.lastCameraX = vec3d.x;
        this.lastCameraY = vec3d.y;
        this.lastCameraZ = vec3d.z;
        this.lastCameraPitch = g;
        this.lastCameraYaw = h;
        this.client.getProfiler().swap("update");

        if (!hasForcedFrustum && this.needsTerrainUpdate) {
            this.needsTerrainUpdate = false;
            this.visibleChunks.clear();
            Queue<WorldRenderer.ChunkInfo> queue = Queues.newArrayDeque();
            Entity.setRenderDistanceMultiplier(
                    MathHelper.clamp((double) this.client.options.viewDistance / 8.0, 1.0, 2.5) * (double) this.client.options.entityDistanceScaling
            );
            boolean bl = this.client.chunkCullingEnabled;
            if (builtChunk != null) {
                if (spectator && this.world.getBlockState(blockPos).isOpaqueFullCube(this.world, blockPos)) {
                    bl = false;
                }
                builtChunk.setRebuildFrame(frame);
                queue.add(((WorldRenderer) (Object) (this)).new ChunkInfo(builtChunk, null, 0));
            } else {
                int j = blockPos.getY() > 0 ? 248 : 8;
                int k = MathHelper.floor(vec3d.x / 16.0) * 16;
                int l = MathHelper.floor(vec3d.z / 16.0) * 16;
                List<WorldRenderer.ChunkInfo> list = Lists.newArrayList();

                for (int m = -this.renderDistance; m <= this.renderDistance; ++m) {
                    for (int n = -this.renderDistance; n <= this.renderDistance; ++n) {
                        ChunkBuilder.BuiltChunk builtChunk2 = ((BuiltChunkStorageAccessor) this.chunks).callGetRenderedChunk(new BlockPos(k + (m << 4) + 8, j, l + (n << 4) + 8));
                        if (builtChunk2 != null && frustum.isVisible(builtChunk2.boundingBox)) {
                            builtChunk2.setRebuildFrame(frame);
                            list.add(((WorldRenderer) (Object) (this)).new ChunkInfo(builtChunk2, null, 0));
                        }
                    }
                }

                list.sort(Comparator.comparingDouble(chunkInfox -> blockPos.getSquaredDistance((((ChunkInfoAccessor) chunkInfox).getChunk().getOrigin().add(8, 8, 8)))));
                queue.addAll(list);
            }

            this.client.getProfiler().push("iteration");

            while (!queue.isEmpty()) {
                WorldRenderer.ChunkInfo chunkInfo = queue.poll();
                ChunkBuilder.BuiltChunk builtChunk3 = ((ChunkInfoAccessor) chunkInfo).getChunk();
                Direction direction = ((ChunkInfoAccessor) chunkInfo).getDirection();
                this.visibleChunks.add(chunkInfo);

                for (Direction direction2 : DIRECTIONS) {
                    ChunkBuilder.BuiltChunk builtChunk4 = this.getAdjacentChunk(blockPos2, builtChunk3, direction2);
                    if ((!bl || !chunkInfo.canCull(direction2.getOpposite()))
                            && (!bl || direction == null || builtChunk3.getData().isVisibleThrough(direction.getOpposite(), direction2))
                            && builtChunk4 != null
                            && builtChunk4.shouldBuild()
                            && builtChunk4.setRebuildFrame(frame)
                            && frustum.isVisible(builtChunk4.boundingBox)) {
                        WorldRenderer.ChunkInfo chunkInfo2 = ((WorldRenderer) (Object) (this)).new ChunkInfo(builtChunk4, direction2, ((ChunkInfoAccessor) chunkInfo).getPropagationLevel() + 1);
                        chunkInfo2.updateCullingState(((ChunkInfoAccessor) chunkInfo).getCullingState(), direction2);
                        queue.add(chunkInfo2);
                    }
                }
            }

            this.client.getProfiler().pop();
        }

        this.client.getProfiler().swap("rebuildNear");
        Set<ChunkBuilder.BuiltChunk> set = this.chunksToRebuild;
        this.chunksToRebuild = Sets.newLinkedHashSet();

        for (WorldRenderer.ChunkInfo chunkInfo : this.visibleChunks) {
            ChunkBuilder.BuiltChunk builtChunk3 = ((ChunkInfoAccessor) chunkInfo).getChunk();
            if (builtChunk3.needsRebuild() || set.contains(builtChunk3)) {
                this.needsTerrainUpdate = true;
                BlockPos blockPos3 = builtChunk3.getOrigin().add(8, 8, 8);
                boolean bl2 = blockPos3.getSquaredDistance(blockPos) < 768.0;
                if (!builtChunk3.needsImportantRebuild() && !bl2) {
                    this.chunksToRebuild.add(builtChunk3);
                } else {
                    this.client.getProfiler().push("build near");
                    this.chunkBuilder.rebuild(builtChunk3);
                    this.client.getProfiler().pop();
                }
            }
        }
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;getViewDistance()F"))
    private float worldpreview_getViewDistance(float original) {
        if (this.isWorldPreview()) {
            return this.client.options.viewDistance * 16;
        }
        return original;
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/debug/DebugRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;DDD)V"))
    private boolean worldpreview_stopDebugRenderer(DebugRenderer instance, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, double cameraX, double cameraY, double cameraZ) {
        return !this.isWorldPreview();
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;", opcode = Opcodes.GETFIELD))
    private ClientWorld modifyWorld(ClientWorld world) {
        if (this.isWorldPreview()) {
            return WorldPreview.world;
        }
        return world;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;player:Lnet/minecraft/client/network/ClientPlayerEntity;", opcode = Opcodes.GETFIELD))
    private ClientPlayerEntity modifyPlayer(ClientPlayerEntity player) {
        if (this.isWorldPreview()) {
            return WorldPreview.player;
        }
        return player;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;"))
    private Entity modifyCameraEntity(Entity entity) {
        if (this.isWorldPreview()) {
            return WorldPreview.player;
        }
        return entity;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;targetedEntity:Lnet/minecraft/entity/Entity;", opcode = Opcodes.GETFIELD))
    private Entity modifyTargetedEntity(Entity entity) {
        if (this.isWorldPreview()) {
            return null;
        }
        return entity;
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.worldRenderer;
    }
}
