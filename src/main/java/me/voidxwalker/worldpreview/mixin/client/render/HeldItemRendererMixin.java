package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.world.ClientWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;", opcode = Opcodes.GETFIELD))
    private ClientWorld modifyWorld(ClientWorld world) {
        if (WorldPreview.inPreview) {
            return WorldPreview.world;
        }
        return world;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;player:Lnet/minecraft/client/network/ClientPlayerEntity;", opcode = Opcodes.GETFIELD))
    private ClientPlayerEntity modifyPlayer(ClientPlayerEntity player) {
        if (WorldPreview.inPreview) {
            return WorldPreview.player;
        }
        return player;
    }

    @ModifyExpressionValue(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;equipProgressMainHand:F",opcode = Opcodes.GETFIELD))
    private float modifyEquipProgress(float original) {
        if (WorldPreview.inPreview) {
            return 1.0f;
        }
        return original;
    }
}
