package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
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

    @ModifyExpressionValue(method = "getFov", at = {@At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;lastMovementFovMultiplier:F"), @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;movementFovMultiplier:F")})
    private float modifyMovementFovMultiplier(float movementFovMultiplier) {
        if (WorldPreview.renderingPreview) {
            return 1.0f;
        }
        return movementFovMultiplier;
    }
}
