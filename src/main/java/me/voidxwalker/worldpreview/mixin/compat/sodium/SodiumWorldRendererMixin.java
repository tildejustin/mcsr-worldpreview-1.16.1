package me.voidxwalker.worldpreview.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SodiumWorldRenderer.class)
public abstract class SodiumWorldRendererMixin {

    @Shadow
    private ClientWorld world;
/*
    // since we create a second WorldRenderer, we rely on "change: Eliminate the assumption that only one client world exists"
    // https://github.com/CaffeineMC/sodium-fabric/commit/9396ff103037e1b34b2818b21f87d6593a3f7748
    // without this change present, this mixin will fail and give us a nice hard crash instead of a lot of issues arising from the same SodiumWorldRenderer being used in both the vanilla and worldpreview WorldRenderer
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;worldRenderer:Lnet/minecraft/client/render/WorldRenderer;"))
    private static WorldRenderer modifyWorldRenderer(WorldRenderer worldRenderer) {
        if (WorldPreview.inPreview) {
            return WorldPreview.worldRenderer;
        }
        return worldRenderer;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;player:Lnet/minecraft/client/network/ClientPlayerEntity;", opcode = Opcodes.GETFIELD))
    private ClientPlayerEntity modifyPlayer(ClientPlayerEntity player) {
        if (this.isWorldPreview()) {
            return WorldPreview.player;
        }
        return player;
    }

 */

    @Unique
    private boolean isWorldPreview() {
        return this.world == WorldPreview.world;
    }
}
