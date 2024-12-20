package me.voidxwalker.worldpreview.mixin.client.render;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    @Unique
    private static final ButtonWidget.PressAction NO_OP = button -> {};

    @Unique
    private boolean showMenu = true;

    protected LevelLoadingScreenMixin(Text title) {
        super(title);
    }

    @WrapWithCondition(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screen/LevelLoadingScreen;renderBackground()V"
            )
    )
    private boolean stopRenderingBackground(LevelLoadingScreen screen) {
        return !WorldPreview.inPreview;
    }

    @ModifyVariable(
            method = "render",
            at = @At("STORE"),
            ordinal = 2
    )
    private int moveChunkMapX(int i) {
        return 45;
    }

    @ModifyVariable(
            method = "render",
            at = @At("STORE"),
            ordinal = 3
    )
    private int moveChunkMapY(int i) {
        return this.height - 75;
    }

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void renderWorldPreview(int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (WorldPreview.updateState()) {
            this.updatePauseMenuWidgets();
        }

        if (!WorldPreview.inPreview || WorldPreview.kill) {
            return;
        }

        assert this.client != null;

        WorldPreview.runAsPreview(() -> {
            WorldPreview.tickPackets();
            WorldPreview.tickEntities();
            WorldPreview.render();
        });

        this.renderPauseMenu(mouseX, mouseY, delta);
    }

    @Unique
    private void renderPauseMenu(int mouseX, int mouseY, float delta) {
        if (this.showMenu) {
            this.fillGradient(0, 0, this.width, this.height + 1, -1072689136, -804253680);
        } else {
            this.drawCenteredString(this.textRenderer, I18n.translate("menu.paused"), this.width / 2, 10, 16777215);
        }
        super.render(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!WorldPreview.inPreview) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.showMenu) {
                if (InputUtil.isKeyPressed(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_F3)) {
                    this.showMenu = false;
                }
            } else {
                this.showMenu = true;
            }
            this.updatePauseMenuWidgets();
            return true;
        }
        return false;
    }

    @Override
    protected void init() {
        this.initPauseMenuWidgets();
        this.updatePauseMenuWidgets();
    }

    @Unique
    private void initPauseMenuWidgets() {
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 24 - 16, 204, 20, I18n.translate("menu.returnToGame"), button -> {
            this.showMenu = false;
            this.updatePauseMenuWidgets();
        }));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 48 - 16, 98, 20, I18n.translate("gui.advancements"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 48 - 16, 98, 20, I18n.translate("gui.stats"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 72 - 16, 98, 20, I18n.translate("menu.sendFeedback"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 72 - 16, 98, 20, I18n.translate("menu.reportBugs"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 96 - 16, 98, 20, I18n.translate("menu.options"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 + 4, this.height / 4 + 96 - 16, 98, 20, I18n.translate("menu.shareToLan"), NO_OP));
        this.addButton(new ButtonWidget(this.width / 2 - 102, this.height / 4 + 120 - 16, 204, 20, I18n.translate("menu.returnToMenu"), button -> {
            WorldPreview.kill = true;
            button.active = false;
        }));
    }

    @Unique
    private void updatePauseMenuWidgets() {
        this.children.stream().filter(e -> e instanceof ButtonWidget).forEach(e -> ((ButtonWidget) e).visible = this.showMenu && WorldPreview.inPreview);
    }

    @Override
    public void removed() {
        WorldPreview.clear();

        WorldPreview.kill = false;
        WorldPreview.inPreview = false;

        WorldPreview.worldRenderer.setWorld(null);
    }
}
