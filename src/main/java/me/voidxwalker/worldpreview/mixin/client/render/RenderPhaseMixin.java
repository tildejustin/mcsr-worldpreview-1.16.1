package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderPhase.class)
public abstract class RenderPhaseMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;worldRenderer:Lnet/minecraft/client/render/WorldRenderer;"))
    private static WorldRenderer modifyWorldRenderer(WorldRenderer worldRenderer) {
        if (WorldPreview.inPreview) {
            return WorldPreview.worldRenderer;
        }
        return worldRenderer;
    }
}
