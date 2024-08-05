package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.TickableSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @WrapWithCondition(
            method = "onEntitySpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundManager;play(Lnet/minecraft/client/sound/SoundInstance;)V"
            )
    )
    private boolean suppressSoundsOnPreview(SoundManager manager, SoundInstance sound) {
        return !WorldPreview.renderingPreview;
    }

    @WrapWithCondition(
            method = "onMobSpawn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/sound/SoundManager;playNextTick(Lnet/minecraft/client/sound/TickableSoundInstance;)V"
            )
    )
    private boolean suppressSoundsOnPreview(SoundManager manager, TickableSoundInstance sound) {
        return !WorldPreview.renderingPreview;
    }
}
