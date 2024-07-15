package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
    private float modifyCameraY(float original) {
        if (this.isWorldPreview()) {
            return WorldPreview.player.getStandingEyeHeight();
        }
        return original;
    }

    @WrapMethod(method = "update")
    public synchronized void synchronizeCameraUpdate(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, Operation<Void> original) {
        original.call(area, focusedEntity, thirdPerson, inverseView, tickDelta);
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.camera;
    }
}
