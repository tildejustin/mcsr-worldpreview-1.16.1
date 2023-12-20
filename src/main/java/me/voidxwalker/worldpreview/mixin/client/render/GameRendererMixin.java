package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyExpressionValue(method = "renderWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;worldRenderer:Lnet/minecraft/client/render/WorldRenderer;"))
    private WorldRenderer modifyWorldRenderer(WorldRenderer worldRenderer) {
        if (WorldPreview.inPreview) {
            return WorldPreview.worldRenderer;
        }
        return worldRenderer;
    }

    @ModifyExpressionValue(method = {"shouldRenderBlockOutline", "updateTargetedEntity", "renderWorld"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;", opcode = Opcodes.GETFIELD))
    private ClientWorld modifyWorld(ClientWorld world) {
        if (WorldPreview.inPreview) {
            return WorldPreview.world;
        }
        return world;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;player:Lnet/minecraft/client/network/ClientPlayerEntity;", opcode = Opcodes.GETFIELD))
    private ClientPlayerEntity modifyPlayer(ClientPlayerEntity player) {
        if (WorldPreview.inPreview) {
            return WorldPreview.player;
        }
        return player;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;camera:Lnet/minecraft/client/render/Camera;", opcode = Opcodes.GETFIELD))
    private Camera modifyCamera(Camera camera) {
        if (WorldPreview.inPreview) {
            return WorldPreview.camera;
        }
        return camera;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;interactionManager:Lnet/minecraft/client/network/ClientPlayerInteractionManager;", opcode = Opcodes.GETFIELD))
    private ClientPlayerInteractionManager modifyInteractionManager(ClientPlayerInteractionManager manager) {
        if (WorldPreview.inPreview) {
            return WorldPreview.INTERACTION_MANAGER;
        }
        return manager;
    }

    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;"))
    private Entity modifyCameraEntity(Entity entity) {
        if (WorldPreview.inPreview) {
            return WorldPreview.player;
        }
        return entity;
    }

    @ModifyExpressionValue(method = "getFov", at = {@At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;lastMovementFovMultiplier:F"), @At(value = "FIELD", target = "Lnet/minecraft/client/render/GameRenderer;movementFovMultiplier:F")})
    private float modifyMovementFovMultiplier(float movementFovMultiplier) {
        if (WorldPreview.inPreview) {
            return 1.0f;
        }
        return movementFovMultiplier;
    }
}
