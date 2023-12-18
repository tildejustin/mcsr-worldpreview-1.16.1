package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin {

    @Shadow
    @Final
    private ClientWorld world;

    @ModifyExpressionValue(method = "onLightUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;worldRenderer:Lnet/minecraft/client/render/WorldRenderer;"))
    private WorldRenderer modifyWorldRenderer(WorldRenderer worldRenderer) {
        if (this.isWorldPreview()) {
            return WorldPreview.worldRenderer;
        }
        return worldRenderer;
    }

    @Unique
    private boolean isWorldPreview() {
        return this.world == WorldPreview.world;
    }
}
