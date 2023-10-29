package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getProfile()Lcom/mojang/authlib/GameProfile;"))
    private static GameProfile modifyGameProfile(ClientPlayNetworkHandler networkHandler, Operation<GameProfile> original) {
        if (WorldPreview.isPreview()) {
            return MinecraftClient.getInstance().getSession().getProfile();
        }
        return original.call(networkHandler);
    }
}
