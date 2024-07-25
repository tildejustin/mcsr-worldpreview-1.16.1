package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReceiver;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.texture.PlayerSkinProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.Executor;

@Mixin(PlayerSkinProvider.class)
public abstract class PlayerSkinProviderMixin {

    @ModifyReceiver(
            method = "loadSkin(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/texture/PlayerSkinProvider$SkinTextureAvailableCallback;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/Executor;execute(Ljava/lang/Runnable;)V"
            )
    )
    private Executor immediatelyGetSkinInPreview(Executor serverWorkerExecutor, Runnable runnable) {
        if (WorldPreview.renderingPreview) {
            return Runnable::run;
        }
        return serverWorkerExecutor;
    }
}
