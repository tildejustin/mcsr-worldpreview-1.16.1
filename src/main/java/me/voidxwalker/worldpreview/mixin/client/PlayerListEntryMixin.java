package me.voidxwalker.worldpreview.mixin.client;

import com.google.common.collect.Maps;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumMap;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Unique
    private static final EnumMap<MinecraftProfileTexture.Type, Identifier> TEXTURES_CACHE = Maps.newEnumMap(MinecraftProfileTexture.Type.class);

    // uses the same textures map for all previews, without this cache skins only get loaded at the end of the preview
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newEnumMap(Ljava/lang/Class;)Ljava/util/EnumMap;"), remap = false)
    private EnumMap<MinecraftProfileTexture.Type, Identifier> modifyTextures(EnumMap<MinecraftProfileTexture.Type, Identifier> textures) {
        if (WorldPreview.renderingPreview) {
            return TEXTURES_CACHE;
        }
        return textures;
    }
}
