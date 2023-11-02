package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.Set;

// Priority is set to 1500 so the fixWorldPreviewChunkRebuilding mixins don't get applied if sodiums overwrite is present
@Mixin(value = WorldRenderer.class, priority = 1500)
public abstract class WorldRendererMixin {

    @WrapWithCondition(method = "setupTerrain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;cancelRebuild()V"), require = 0)
    private boolean fixWorldPreviewChunkRebuilding(ChunkBuilder.BuiltChunk builtChunk) {
        return !this.isWorldPreview();
    }

    @WrapWithCondition(method = "setupTerrain", at = @At(value = "INVOKE", target = "Ljava/util/Set;addAll(Ljava/util/Collection;)Z"), require = 0)
    private boolean fixWorldPreviewChunkRebuilding(Set<ChunkBuilder.BuiltChunk> set, Collection<ChunkBuilder.BuiltChunk> collection) {
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
