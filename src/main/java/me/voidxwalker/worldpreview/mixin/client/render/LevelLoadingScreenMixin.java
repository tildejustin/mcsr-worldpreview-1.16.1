package me.voidxwalker.worldpreview.mixin.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import me.voidxwalker.worldpreview.WorldPreview;
import me.voidxwalker.worldpreview.mixin.access.GameRendererAccessor;
import me.voidxwalker.worldpreview.mixin.access.WorldRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("deprecation")
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    @Unique
    private static final ButtonWidget.PressAction NO_OP = button -> {};

    @Unique
    private boolean showMenu = true;

    @Unique
    private boolean freezePreview = false;

    protected LevelLoadingScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void worldpreview_init(WorldGenerationProgressTracker progressProvider, CallbackInfo ci) {
        WorldPreview.calculatedSpawn = true;
        KeyBinding.unpressAll();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/LevelLoadingScreen;renderBackground(Lnet/minecraft/client/util/math/MatrixStack;)V"))
    private void worldpreview_stopBackgroundRender(LevelLoadingScreen instance, MatrixStack matrixStack) {
        if (WorldPreview.camera == null) {
            instance.renderBackground(matrixStack);
        }
    }

    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 2)
    private int moveChunkMapX(int i) {
        return 45;
    }

    @ModifyVariable(method = "render", at = @At("STORE"), ordinal = 3)
    private int moveChunkMapY(int i) {
        return this.height - 75;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void worldpreview_render(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (WorldPreview.world != null && WorldPreview.player != null && !this.freezePreview) {
            assert this.client != null;
            if (((WorldRendererAccessor) WorldPreview.worldRenderer).getWorld() == null && WorldPreview.calculatedSpawn) {
                WorldPreview.worldRenderer.setWorld(WorldPreview.world);
            }
            if (((WorldRendererAccessor) WorldPreview.worldRenderer).getWorld() != null) {
                KeyBinding.unpressAll();
                WorldPreview.kill = false;
                MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager().update(0);
                if (WorldPreview.camera == null) {
                    WorldPreview.player.refreshPositionAndAngles(WorldPreview.player.getX(), WorldPreview.player.getEyeY(), WorldPreview.player.getZ(), 0.0F, 0.0F);
                    WorldPreview.camera = new Camera();
                    WorldPreview.camera.update(WorldPreview.world, WorldPreview.player, this.client.options.perspective > 0, this.client.options.perspective == 2, 0.2F);
                    WorldPreview.inPreview = true;
                }
                MatrixStack matrixStack = new MatrixStack();
                matrixStack.peek().getModel().multiply(this.worldpreview_getBasicProjectionMatrix());
                Matrix4f matrix4f = matrixStack.peek().getModel();
                RenderSystem.matrixMode(5889);
                RenderSystem.loadIdentity();
                RenderSystem.multMatrix(matrix4f);
                RenderSystem.matrixMode(5888);
                MatrixStack m = new MatrixStack();
                m.multiply(Vector3f.POSITIVE_X.getDegreesQuaternion(WorldPreview.camera.getPitch()));
                m.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(WorldPreview.camera.getYaw() + 180.0F));
                WorldPreview.worldRenderer.render(m, 0.0F, 1000000, ((GameRendererAccessor) this.client.gameRenderer).callShouldRenderBlockOutline(), WorldPreview.camera, MinecraftClient.getInstance().gameRenderer, MinecraftClient.getInstance().gameRenderer.getLightmapTextureManager(), matrix4f);
                RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                ((GameRendererAccessor) this.client.gameRenderer).callRenderHand(matrices, WorldPreview.camera, 0.0F);
                WorldPreview.worldRenderer.drawEntityOutlinesFramebuffer();
                Window window = this.client.getWindow();
                RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                RenderSystem.matrixMode(5889);
                RenderSystem.loadIdentity();
                RenderSystem.ortho(0.0D, (double) window.getFramebufferWidth() / window.getScaleFactor(), (double) window.getFramebufferHeight() / window.getScaleFactor(), 0.0D, 1000.0D, 3000.0D);
                RenderSystem.matrixMode(5888);
                RenderSystem.loadIdentity();
                RenderSystem.translatef(0.0F, 0.0F, -2000.0F);
                DiffuseLighting.enableGuiDepthLighting();
                RenderSystem.defaultAlphaFunc();
                this.client.inGameHud.render(matrices, 0.0F);
                RenderSystem.clear(256, MinecraftClient.IS_SYSTEM_MAC);
                this.worldpreview_renderPauseMenu(matrices, mouseX, mouseY, delta);
            }
        }
    }

    @Unique
    private void worldpreview_renderPauseMenu(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        if (this.showMenu) {
            this.fillGradient(matrices, 0, 0, this.width, this.height, -1072689136, -804253680);
            super.render(matrices, mouseX, mouseY, delta);
        } else {
            this.drawCenteredText(matrices, this.textRenderer, new TranslatableText("menu.paused"), this.width / 2, 10, 16777215);
        }
    }

    @Unique
    private Matrix4f worldpreview_getBasicProjectionMatrix() {
        MatrixStack matrixStack = new MatrixStack();
        matrixStack.peek().getModel().loadIdentity();
        matrixStack.peek().getModel().multiply(Matrix4f.viewboxMatrix(this.client.options.fov, (float) this.client.getWindow().getFramebufferWidth() / (float) this.client.getWindow().getFramebufferHeight(), 0.05F, this.client.options.viewDistance * 16 * 4.0F));
        return matrixStack.peek().getModel();
    }

    @Unique
    private void worldpreview_initWidgets() {
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 24 - 16, 204, 20, new TranslatableText("menu.returnToGame"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 48 - 16, 98, 20, new TranslatableText("gui.advancements"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 48 - 16, 98, 20, new TranslatableText("gui.stats"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 72 - 16, 98, 20, new TranslatableText("menu.sendFeedback"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 72 - 16, 98, 20, new TranslatableText("menu.reportBugs"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 96 - 16, 98, 20, new TranslatableText("menu.options"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 96 - 16, 98, 20, new TranslatableText("menu.shareToLan"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 120 - 16, 204, 20, new TranslatableText("menu.returnToMenu"), button -> {
            WorldPreview.kill = true;
            button.active = false;
        }));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.showMenu) {
                if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_F3)) {
                    this.showMenu = false;
                    this.children.clear();
                }
            } else {
                this.showMenu = true;
                this.worldpreview_initWidgets();
            }
            return true;
        }
        if (WorldPreview.freezeKey.matchesKey(keyCode, scanCode)) {
            this.freezePreview = !this.freezePreview;
            return true;
        }
        if (WorldPreview.resetKey.matchesKey(keyCode, scanCode)) {
            WorldPreview.kill = true;
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        super.init();
        if (this.showMenu) {
            this.worldpreview_initWidgets();
        }
    }

    @Override
    public void removed() {
        WorldPreview.kill = false;
    }
}
