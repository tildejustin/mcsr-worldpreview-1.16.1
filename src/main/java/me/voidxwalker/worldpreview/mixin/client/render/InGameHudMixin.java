package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @ModifyExpressionValue(method = "renderHeldItemTooltip", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/InGameHud;currentStack:Lnet/minecraft/item/ItemStack;", opcode = Opcodes.GETFIELD))
    private ItemStack modifyCurrentStack(ItemStack currentStack) {
        if (WorldPreview.renderingPreview) {
            return WorldPreview.player.getMainHandStack();
        }
        return currentStack;
    }

    @ModifyExpressionValue(method = "renderHeldItemTooltip", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/InGameHud;heldItemTooltipFade:I", opcode = Opcodes.GETFIELD))
    private int modifyHeldItemTooltipFade(int heldItemTooltipFade) {
        if (WorldPreview.renderingPreview) {
            return 40;
        }
        return heldItemTooltipFade;
    }
}
