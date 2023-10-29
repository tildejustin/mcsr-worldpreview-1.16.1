package me.voidxwalker.worldpreview.mixin.access;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Invoker
    boolean callShouldRenderBlockOutline();

    @Invoker
    void callRenderHand(MatrixStack matrices, Camera camera, float tickDelta);
}
