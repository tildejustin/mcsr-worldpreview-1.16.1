package me.voidxwalker.worldpreview.mixin.compat.sodium;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.ObjectList;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@SuppressWarnings("deprecation")
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
    @Final
    private VertexFormat vertexFormat;

    @Shadow
    private double lastTranslucentSortY;

    @Shadow
    private double lastTranslucentSortX;

    @Shadow
    private double lastTranslucentSortZ;

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

    @Shadow @Nullable
    protected abstract ChunkBuilder.BuiltChunk getAdjacentChunk(BlockPos pos, ChunkBuilder.BuiltChunk chunk, Direction direction);

    @ModifyArg(method = "reload", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/BuiltChunkStorage;<init>(Lnet/minecraft/client/render/chunk/ChunkBuilder;Lnet/minecraft/world/World;ILnet/minecraft/client/render/WorldRenderer;)V"), index = 2)
    private int useVanillaBuiltChunkStorage(int viewDistance) {
        if (this.isWorldPreview()) {
            return this.client.options.viewDistance;
        }
        return viewDistance;
    }
    
    @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
    private void useVanillaSetupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum, int frame, boolean spectator, CallbackInfo ci) {
        if (!this.isWorldPreview()) {
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
        if (this.cameraChunkX != WorldPreview.player.chunkX || this.cameraChunkY != WorldPreview.player.chunkY || this.cameraChunkZ != WorldPreview.player.chunkZ || d * d + e * e + f * f > 16.0) {
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
        this.needsTerrainUpdate = this.needsTerrainUpdate || !this.chunksToRebuild.isEmpty() || vec3d.x != this.lastCameraX || vec3d.y != this.lastCameraY || vec3d.z != this.lastCameraZ || (double)g != this.lastCameraPitch || (double)h != this.lastCameraYaw;
        this.lastCameraX = vec3d.x;
        this.lastCameraY = vec3d.y;
        this.lastCameraZ = vec3d.z;
        this.lastCameraPitch = g;
        this.lastCameraYaw = h;
        this.client.getProfiler().swap("update");
        if (!hasForcedFrustum && this.needsTerrainUpdate) {
            this.needsTerrainUpdate = false;
            this.visibleChunks.clear();
            ArrayDeque<WorldRenderer.ChunkInfo> queue = Queues.newArrayDeque();
            Entity.setRenderDistanceMultiplier(MathHelper.clamp((double)this.client.options.viewDistance / 8.0, 1.0, 2.5) * (double)this.client.options.entityDistanceScaling);
            boolean bl = this.client.chunkCullingEnabled;
            if (builtChunk == null) {
                int j = blockPos.getY() > 0 ? 248 : 8;
                int k = MathHelper.floor(vec3d.x / 16.0) * 16;
                int l = MathHelper.floor(vec3d.z / 16.0) * 16;
                List<WorldRenderer.ChunkInfo> list = Lists.newArrayList();
                for (int m = -this.renderDistance; m <= this.renderDistance; ++m) {
                    for (int n = -this.renderDistance; n <= this.renderDistance; ++n) {
                        ChunkBuilder.BuiltChunk builtChunk2 = ((BuiltChunkStorageAccessor) this.chunks).callGetRenderedChunk(new BlockPos(k + (m << 4) + 8, j, l + (n << 4) + 8));
                        if (builtChunk2 == null || !frustum.isVisible(builtChunk2.boundingBox)) continue;
                        builtChunk2.setRebuildFrame(frame);
                        list.add(((WorldRenderer) (Object) this).new ChunkInfo(builtChunk2, null, 0));
                    }
                }
                list.sort(Comparator.comparingDouble(chunkInfo -> blockPos.getSquaredDistance(((ChunkInfoAccessor) chunkInfo).getChunk().getOrigin().add(8, 8, 8))));
                queue.addAll(list);
            } else {
                if (spectator && this.world.getBlockState(blockPos).isOpaqueFullCube(this.world, blockPos)) {
                    bl = false;
                }
                builtChunk.setRebuildFrame(frame);
                queue.add(((WorldRenderer) (Object) this).new ChunkInfo(builtChunk, null, 0));
            }
            this.client.getProfiler().push("iteration");
            while (!queue.isEmpty()) {
                WorldRenderer.ChunkInfo chunkInfo2 = queue.poll();
                ChunkBuilder.BuiltChunk builtChunk3 = ((ChunkInfoAccessor) chunkInfo2).getChunk();
                Direction direction = ((ChunkInfoAccessor) chunkInfo2).getDirection();
                this.visibleChunks.add(chunkInfo2);
                for (Direction direction2 : DIRECTIONS) {
                    ChunkBuilder.BuiltChunk builtChunk4 = this.getAdjacentChunk(blockPos2, builtChunk3, direction2);
                    if (bl && chunkInfo2.canCull(direction2.getOpposite()) || bl && direction != null && !builtChunk3.getData().isVisibleThrough(direction.getOpposite(), direction2) || builtChunk4 == null || !builtChunk4.shouldBuild() || !builtChunk4.setRebuildFrame(frame) || !frustum.isVisible(builtChunk4.boundingBox)) continue;
                    WorldRenderer.ChunkInfo chunkInfo22 = ((WorldRenderer) (Object) this).new ChunkInfo(builtChunk4, direction2, ((ChunkInfoAccessor) chunkInfo2).getPropagationLevel() + 1);
                    chunkInfo22.updateCullingState(((ChunkInfoAccessor) chunkInfo2).getCullingState(), direction2);
                    queue.add(chunkInfo22);
                }
            }
            this.client.getProfiler().pop();
        }
        this.client.getProfiler().swap("rebuildNear");
        Set<ChunkBuilder.BuiltChunk> set = this.chunksToRebuild;
        this.chunksToRebuild = Sets.newLinkedHashSet();
        for (WorldRenderer.ChunkInfo chunkInfo3 : this.visibleChunks) {
            boolean bl2;
            ChunkBuilder.BuiltChunk builtChunk3 = ((ChunkInfoAccessor) chunkInfo3).getChunk();
            if (!builtChunk3.needsRebuild() && !set.contains(builtChunk3)) continue;
            this.needsTerrainUpdate = true;
            BlockPos blockPos3 = builtChunk3.getOrigin().add(8, 8, 8);
            bl2 = blockPos3.getSquaredDistance(blockPos) < 768.0;
            if (builtChunk3.needsImportantRebuild() || bl2) {
                this.client.getProfiler().push("build near");
                this.chunkBuilder.rebuild(builtChunk3);
                // WorldRendererMixin#fixWorldPreviewChunkRebuilding
                //builtChunk3.cancelRebuild();
                this.client.getProfiler().pop();
                continue;
            }
            this.chunksToRebuild.add(builtChunk3);
        }
        // WorldRendererMixin#fixWorldPreviewChunkRebuilding
        //this.chunksToRebuild.addAll(set);
        this.client.getProfiler().pop();

        ci.cancel();
    }

    @Inject(method = "renderLayer", at = @At("HEAD"), cancellable = true)
    private void useVanillaRenderLayer(RenderLayer renderLayer, MatrixStack matrixStack, double d, double e, double f, CallbackInfo ci) {
        if (!this.isWorldPreview()) {
            return;
        }

        renderLayer.startDrawing();
        if (renderLayer == RenderLayer.getTranslucent()) {
            this.client.getProfiler().push("translucent_sort");
            double g = d - this.lastTranslucentSortX;
            double h = e - this.lastTranslucentSortY;
            double i = f - this.lastTranslucentSortZ;
            if (g * g + h * h + i * i > 1.0) {
                this.lastTranslucentSortX = d;
                this.lastTranslucentSortY = e;
                this.lastTranslucentSortZ = f;
                int j = 0;
                for (WorldRenderer.ChunkInfo chunkInfo : this.visibleChunks) {
                    if (j >= 15 || !((ChunkInfoAccessor) chunkInfo).getChunk().scheduleSort(renderLayer, this.chunkBuilder)) continue;
                    ++j;
                }
            }
            this.client.getProfiler().pop();
        }
        this.client.getProfiler().push("filterempty");
        this.client.getProfiler().swap(() -> "render_" + renderLayer);
        boolean bl = renderLayer != RenderLayer.getTranslucent();
        ListIterator<WorldRenderer.ChunkInfo> objectListIterator = this.visibleChunks.listIterator(bl ? 0 : this.visibleChunks.size());
        while (bl ? objectListIterator.hasNext() : objectListIterator.hasPrevious()) {
            WorldRenderer.ChunkInfo chunkInfo2 = bl ? objectListIterator.next() : objectListIterator.previous();
            ChunkBuilder.BuiltChunk builtChunk = ((ChunkInfoAccessor) chunkInfo2).getChunk();
            if (builtChunk.getData().isEmpty(renderLayer)) continue;
            VertexBuffer vertexBuffer = builtChunk.getBuffer(renderLayer);
            matrixStack.push();
            BlockPos blockPos = builtChunk.getOrigin();
            matrixStack.translate((double)blockPos.getX() - d, (double)blockPos.getY() - e, (double)blockPos.getZ() - f);
            vertexBuffer.bind();
            this.vertexFormat.startDrawing(0L);
            vertexBuffer.draw(matrixStack.peek().getModel(), 7);
            matrixStack.pop();
        }
        VertexBuffer.unbind();
        RenderSystem.clearCurrentColor();
        this.vertexFormat.endDrawing();
        this.client.getProfiler().pop();
        renderLayer.endDrawing();

        ci.cancel();
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.worldRenderer;
    }
}
