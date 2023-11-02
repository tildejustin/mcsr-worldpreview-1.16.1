package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getNetworkHandler()Lnet/minecraft/client/network/ClientPlayNetworkHandler;"))
    private ClientPlayNetworkHandler modifyNetworkHandler(ClientPlayNetworkHandler networkHandler) {
        if (this.isWorldPreview()) {
            return WorldPreview.DUMMY_NETWORK_HANDLER;
        }
        return networkHandler;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;getPlayerListEntry(Ljava/util/UUID;)Lnet/minecraft/client/network/PlayerListEntry;"))
    private PlayerListEntry modifyPlayerListEntry(PlayerListEntry playerListEntry) {
        if (this.isWorldPreview()) {
            return WorldPreview.playerListEntry;
        }
        return playerListEntry;
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.player;
    }
}
