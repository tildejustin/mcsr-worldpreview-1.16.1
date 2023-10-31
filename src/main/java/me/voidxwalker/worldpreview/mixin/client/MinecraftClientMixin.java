package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.mojang.datafixers.util.Function4;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.DataPackSettings;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

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

    @Shadow public abstract void disconnect();

    @Shadow public abstract void openScreen(@Nullable Screen screen);
    
    @Inject(method = "isFabulousGraphicsOrBetter", at = @At("RETURN"), cancellable = true)
    private static void stopFabulousDuringPreview(CallbackInfoReturnable<Boolean> cir) {
        if (WorldPreview.inPreview) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/RegistryTracker$Modifiable;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Lnet/minecraft/server/integrated/IntegratedServer;isLoading()Z"), cancellable = true)
    private void resetPreview(CallbackInfo ci) {
        if (WorldPreview.inPreview && WorldPreview.kill && this.server != null) {
            WorldPreview.log(Level.INFO, "Leaving world generation");
            ((WPMinecraftServer) this.server).worldpreview$kill();
            this.disconnect();
            this.openScreen(new TitleScreen());
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "startIntegratedServer(Ljava/lang/String;Lnet/minecraft/util/registry/RegistryTracker$Modifiable;Ljava/util/function/Function;Lcom/mojang/datafixers/util/Function4;ZLnet/minecraft/client/MinecraftClient$WorldLoadAction;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;startServer(Ljava/util/function/Function;)Lnet/minecraft/server/MinecraftServer;"))
    private MinecraftServer setIsNewWorld(MinecraftServer server, String worldName, RegistryTracker.Modifiable registryTracker, Function<LevelStorage.Session, DataPackSettings> function, Function4<LevelStorage.Session, RegistryTracker.Modifiable, ResourceManager, DataPackSettings, SaveProperties> function4, boolean safeMode, MinecraftClient.WorldLoadAction worldLoadAction) {
        ((WPMinecraftServer) server).worldpreview$setIsNewWorld(worldLoadAction == MinecraftClient.WorldLoadAction.CREATE);
        return server;
    }

    @WrapWithCondition(method = "reset", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private boolean smoothTransition(MinecraftClient client, Screen screen) {
        return !(this.currentScreen instanceof LevelLoadingScreen);
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/resource/ReloadableResourceManager;registerListener(Lnet/minecraft/resource/ResourceReloadListener;)V", ordinal = 11))
    private void createWorldPreviewRenderer(CallbackInfo ci) {
        WorldPreview.worldRenderer = new WorldRenderer(MinecraftClient.getInstance(), new BufferBuilderStorage());
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/Window;swapBuffers()V", shift = At.Shift.AFTER))
    private void logWorldPreviewStart(CallbackInfo ci) {
        if (WorldPreview.inPreview && !WorldPreview.renderingPreview) {
            WorldPreview.renderingPreview = true;
            WorldPreview.log(Level.INFO, "Starting Preview at (" + WorldPreview.player.getX() + ", " + Math.floor(WorldPreview.player.getY()) + ", " + WorldPreview.player.getZ() + ")");
        }
    }
}
