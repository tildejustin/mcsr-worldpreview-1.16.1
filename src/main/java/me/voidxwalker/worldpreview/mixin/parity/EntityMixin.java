package me.voidxwalker.worldpreview.mixin.parity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/concurrent/atomic/AtomicInteger;incrementAndGet()I"
            )
    )
    private int doNotIncrementEntityIdOnPreview(AtomicInteger MAX_ENTITY_ID, Operation<Integer> original) {
        // for vanilla parity (where these entities wouldn't be created) we skip incrementing MAX_ENTITY_ID
        // the entity id will be set in the ClientPlayNetworkHandler anyway
        if (MinecraftClient.getInstance().isOnThread() && WorldPreview.renderingPreview) {
            return -1;
        }
        return original.call(MAX_ENTITY_ID);
    }
}
