package me.voidxwalker.worldpreview.mixin.server;

import net.minecraft.network.NetworkEncryptionUtils;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.security.KeyPair;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin extends MinecraftServerMixin {

    @Redirect(
            method = "setupServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/NetworkEncryptionUtils;generateServerKeyPair()Ljava/security/KeyPair;"
            )
    )
    private KeyPair generateKeyPairLate() {
        return null;
    }

    @Inject(
            method = "setupServer",
            at = @At("RETURN")
    )
    private void generateKeyPairLate(CallbackInfoReturnable<Boolean> cir) {
        if (!this.killed) {
            this.setKeyPair(NetworkEncryptionUtils.generateServerKeyPair());
        }
    }
}
