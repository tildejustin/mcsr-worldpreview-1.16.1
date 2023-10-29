package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;", opcode = Opcodes.GETFIELD))
    private ClientWorld modifyWorld(ClientWorld world) {
        if (WorldPreview.isPreview() || world == null) {
            return WorldPreview.world;
        }
        return world;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;player:Lnet/minecraft/client/network/ClientPlayerEntity;", opcode = Opcodes.GETFIELD))
    private ClientPlayerEntity modifyPlayer(ClientPlayerEntity player) {
        if (WorldPreview.isPreview() || player == null) {
            return WorldPreview.player;
        }
        return player;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;interactionManager:Lnet/minecraft/client/network/ClientPlayerInteractionManager;", opcode = Opcodes.GETFIELD))
    private ClientPlayerInteractionManager modifyInteractionManager(ClientPlayerInteractionManager manager) {
        if (WorldPreview.isPreview() || manager == null) {
            return WorldPreview.DUMMY_INTERACTION_MANAGER;
        }
        return manager;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;"))
    private Entity modifyCameraEntity(Entity entity) {
        if (WorldPreview.isPreview() || entity == null) {
            return WorldPreview.player;
        }
        return entity;
    }
}
