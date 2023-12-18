package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow
    public World world;

    // since we use the serverside BlockEntity, getting its BlockState requires synchronization with the ServerWorld
    // we don't want that, so instead we redirect to the WorldPreview world
    @WrapOperation(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/BlockEntity;getCachedState()Lnet/minecraft/block/BlockState;"))
    private BlockState fixOffThreadGetBlockStateCalls(BlockEntity blockEntity, Operation<BlockState> original) {
        if (this.isWorldPreview()) {
            return WorldPreview.world.getBlockState(blockEntity.getPos());
        }
        return original.call(blockEntity);
    }

    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/render/block/entity/BlockEntityRenderer;Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/BlockEntity;getWorld()Lnet/minecraft/world/World;"))
    private static World fixOffThreadGetLightmapCalls(World world) {
        if (WorldPreview.inPreview) {
            return WorldPreview.world;
        }
        return world;
    }

    @Unique
    private boolean isWorldPreview() {
        return this.world == WorldPreview.world;
    }
}
