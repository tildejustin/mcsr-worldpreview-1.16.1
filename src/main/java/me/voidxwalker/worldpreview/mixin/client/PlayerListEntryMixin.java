package me.voidxwalker.worldpreview.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import me.voidxwalker.worldpreview.WorldPreview;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumMap;
import java.util.Map;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Unique
    private static final Map<MinecraftProfileTexture.Type, Identifier> TEXTURES_CACHE = new EnumMap<>(MinecraftProfileTexture.Type.class);

    // uses the same textures map for all previews, without this cache skins only get loaded at the end of the preview
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/PlayerListEntry;textures:Ljava/util/Map;", opcode = Opcodes.GETFIELD))
    private Map<MinecraftProfileTexture.Type, Identifier> modifyTextures(Map<MinecraftProfileTexture.Type, Identifier> textures) {
        if (this.isWorldPreview()) {
            return TEXTURES_CACHE;
        }
        return textures;
    }

    @Unique
    private boolean isWorldPreview() {
        return (Object) this == WorldPreview.playerListEntry;
    }
}
