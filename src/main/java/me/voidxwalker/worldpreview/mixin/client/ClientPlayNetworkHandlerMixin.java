package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow
    private ClientWorld world;

    @ModifyExpressionValue(method = "*", at = {@At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;", opcode = Opcodes.GETFIELD), @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;world:Lnet/minecraft/client/world/ClientWorld;", opcode = Opcodes.GETFIELD)})
    private ClientWorld modifyWorld(ClientWorld world) {
        if (this.isWorldPreview()) {
            // we set the world field for compatibility with starlight's mixin (ClientPacketListenerMixin)
            return this.world = WorldPreview.world;
        }
        return world;
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.NETWORK_HANDLER;
    }
}
