package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getNetworkHandler()Lnet/minecraft/client/network/ClientPlayNetworkHandler;"))
    private ClientPlayNetworkHandler modifyNetworkHandler(ClientPlayNetworkHandler networkHandler) {
        if (this.isWorldPreview()) {
            return WorldPreview.DUMMY_NETWORK_HANDLER;
        }
        return networkHandler;
    }

    @Inject(method = "isSpectator", at = @At("HEAD"), cancellable = true)
    private void modifyIsSpectator(CallbackInfoReturnable<Boolean> cir) {
        if (this.isWorldPreview()) {
            cir.setReturnValue(GameMode.SPECTATOR == WorldPreview.gameMode);
        }
    }

    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void modifyIsCreative(CallbackInfoReturnable<Boolean> cir) {
        if (this.isWorldPreview()) {
            cir.setReturnValue(GameMode.CREATIVE == WorldPreview.gameMode);
        }
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.player;
    }
}
