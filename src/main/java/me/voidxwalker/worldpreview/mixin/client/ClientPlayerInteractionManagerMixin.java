package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.GameMode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;gameMode:Lnet/minecraft/world/GameMode;", opcode = Opcodes.GETFIELD))
    private GameMode modifyGameMode(GameMode gameMode) {
        if (this.isWorldPreview()) {
            return WorldPreview.playerListEntry.getGameMode();
        }
        return gameMode;
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.INTERACTION_MANAGER;
    }
}
