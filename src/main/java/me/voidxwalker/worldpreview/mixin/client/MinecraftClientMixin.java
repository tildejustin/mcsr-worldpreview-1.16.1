package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.locks.LockSupport;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    @Nullable
    public ClientWorld world;
    @Shadow
    @Nullable
    public Screen currentScreen;
    @Shadow
    private @Nullable IntegratedServer server;

    @Shadow
    protected abstract void render(boolean tick);

    @Inject(method = "isFabulousGraphicsOrBetter", at = @At("RETURN"), cancellable = true)
    private static void worldpreview_stopFabulous(CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftClient.getInstance().currentScreen instanceof LevelLoadingScreen && MinecraftClient.getInstance().world == null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/RegistryTracker$Modifiable;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/server/integrated/IntegratedServer;isLoading()Z"), cancellable = true)
    private void worldpreview_onHotKeyPressed(CallbackInfo ci) {
        if (WorldPreview.inPreview) {
            if (WorldPreview.resetKey.wasPressed() || WorldPreview.kill == -1) {
                WorldPreview.log(Level.INFO, "Leaving world generation");
                WorldPreview.kill = 1;
                while (WorldPreview.inPreview) {
                    LockSupport.park(); // I am at a loss to emphasize how bad of an idea Thread.yield() here is.
                }
                this.server.shutdown();
                MinecraftClient.getInstance().disconnect();
                WorldPreview.kill = 0;
                MinecraftClient.getInstance().openScreen(new TitleScreen());
                ci.cancel();
            }
            if (WorldPreview.freezeKey.wasPressed()) {
                WorldPreview.freezePreview = !WorldPreview.freezePreview;
                if (WorldPreview.freezePreview) {
                    WorldPreview.log(Level.INFO, "Freezing Preview"); // insert anchiale joke
                } else {
                    WorldPreview.log(Level.INFO, "Unfreezing Preview");
                }
            }
        }
    }

    @Inject(method = "startIntegratedServer(Ljava/lang/String;)V", at = @At("HEAD"))
    private void worldpreview_isExistingWorld(CallbackInfo ci) {
        WorldPreview.existingWorld = true;
    }

    @WrapWithCondition(method = "reset", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private boolean smoothTransition(MinecraftClient client, Screen screen) {
        return !(this.currentScreen instanceof LevelLoadingScreen && screen != null);
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ReloadableResourceManager;registerListener(Lnet/minecraft/resource/ResourceReloadListener;)V", ordinal = 11))
    private void worldpreview_createWorldRenderer(CallbackInfo ci) {
        WorldPreview.worldRenderer = new WorldRenderer(MinecraftClient.getInstance(), new BufferBuilderStorage());
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    private void worldpreview_reset(CallbackInfo ci) {
        synchronized (WorldPreview.LOCK) {
            WorldPreview.world = null;
            WorldPreview.player = null;
            WorldPreview.camera = null;
            WorldPreview.gameMode = null;
            if (WorldPreview.worldRenderer != null) {
                WorldPreview.worldRenderer.setWorld(null);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;swapBuffers()V", shift = At.Shift.AFTER))
    private void worldpreview_actuallyInPreview(CallbackInfo ci) {
        if (WorldPreview.inPreview && !WorldPreview.renderingPreview) {
            WorldPreview.renderingPreview = true;
            WorldPreview.log(Level.INFO, "Starting Preview at (" + WorldPreview.player.getX() + ", " + Math.floor(WorldPreview.player.getY()) + ", " + WorldPreview.player.getZ() + ")");
        }
    }
}
