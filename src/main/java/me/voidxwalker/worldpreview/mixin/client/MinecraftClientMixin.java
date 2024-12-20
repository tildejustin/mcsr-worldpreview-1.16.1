package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.compat.StateOutputCompat;
import me.voidxwalker.worldpreview.interfaces.WPMinecraftServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    @Nullable
    public Screen currentScreen;
    @Shadow
    @Nullable
    private IntegratedServer server;

    @Shadow
    public abstract void disconnect();

    @Shadow
    public abstract void openScreen(@Nullable Screen screen);

    @Inject(
            method = "startIntegratedServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/integrated/IntegratedServer;isLoading()Z"
            ),
            cancellable = true
    )
    private void resetPreview(CallbackInfo ci) {
        if (this.server != null && WorldPreview.inPreview && WorldPreview.kill) {
            if (!((WPMinecraftServer) this.server).worldpreview$kill()) {
                return;
            }
            WorldPreview.LOGGER.info("Leaving world generation");
            this.disconnect();
            this.openScreen(new TitleScreen());
            ci.cancel();
        }
    }

    @WrapWithCondition(
            method = "reset",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/MinecraftClient;openScreen(Lnet/minecraft/client/gui/screen/Screen;)V"
            )
    )
    private boolean smoothTransition(MinecraftClient client, Screen screen) {
        return !(WorldPreview.inPreview && this.currentScreen instanceof LevelLoadingScreen && screen instanceof ProgressScreen);
    }

    @ModifyExpressionValue(
            method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/MinecraftClient;world:Lnet/minecraft/client/world/ClientWorld;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private ClientWorld waitOnKilledServers(ClientWorld world) {
        if (world == null && WorldPreview.inPreview && WorldPreview.kill) {
            return WorldPreview.world;
        }
        return world;
    }

    @Inject(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;<init>(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/render/BufferBuilderStorage;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void createWorldPreviewRenderer(CallbackInfo ci) {
        WorldPreview.worldRenderer = new WorldRenderer(MinecraftClient.getInstance(), new BufferBuilderStorage());
    }

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/util/Window;swapBuffers()V",
                    shift = At.Shift.AFTER
            )
    )
    private void logWorldPreviewStart(CallbackInfo ci) {
        if (WorldPreview.logPreviewStart) {
            WorldPreview.LOGGER.info("Starting Preview at ({}, {}, {})", WorldPreview.player.getX(), WorldPreview.player.getY(), WorldPreview.player.getZ());
            if (WorldPreview.HAS_STATEOUTPUT) {
                StateOutputCompat.outputPreviewing();
            }
            WorldPreview.logPreviewStart = false;
        }
    }
}
