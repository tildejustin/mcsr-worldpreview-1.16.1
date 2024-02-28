package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "onResized", at = @At("TAIL"))
    private void resizeWorldRenderer(int i, int j, CallbackInfo ci) {
        WorldPreview.worldRenderer.onResized(i, j);
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;camera:Lnet/minecraft/client/render/Camera;", opcode = Opcodes.GETFIELD))
    private Camera modifyCamera(Camera camera) {
        if (WorldPreview.renderingPreview) {
            return WorldPreview.camera;
        }
        return camera;
    }

    // to be replaced by @WrapMethod in Camera when it comes out
    // https://github.com/LlamaLad7/MixinExtras/issues/65
    @WrapOperation(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V"))
    private void synchronizeCameraUpdating(Camera camera, BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, Operation<Void> original) {
        synchronized (camera) {
            original.call(camera, area, focusedEntity, thirdPerson, inverseView, tickDelta);
        }
    }

    @ModifyExpressionValue(method = "getFov", at = {@At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;lastMovementFovMultiplier:F"), @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;movementFovMultiplier:F")})
    private float modifyMovementFovMultiplier(float movementFovMultiplier) {
        if (WorldPreview.renderingPreview) {
            return Math.min(Math.max(WorldPreview.player.getSpeed(), 0.1f), 1.5f);
        }
        return movementFovMultiplier;
    }
}
