package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;


@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyExpressionValue(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = {@At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;equipProgressMainHand:F", opcode = Opcodes.GETFIELD), @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;prevEquipProgressMainHand:F", opcode = Opcodes.GETFIELD), @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;equipProgressOffHand:F", opcode = Opcodes.GETFIELD), @At(value = "FIELD", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;prevEquipProgressOffHand:F", opcode = Opcodes.GETFIELD)})
    private float modifyEquipProgress(float equipProgress) {
        if (WorldPreview.renderingPreview) {
            return 1.0f;
        }
        return equipProgress;
    }
}
