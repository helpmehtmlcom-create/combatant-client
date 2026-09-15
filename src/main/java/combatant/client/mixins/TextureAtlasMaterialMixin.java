/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.render.engine.material.MaterialAtlasManager;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Keeps Combatant's companion material atlases on the exact Minecraft block-atlas lifecycle. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMaterialMixin {
    @Shadow @Final private Identifier location;
    @Shadow private List<TextureAtlasSprite> sprites;
    @Shadow private List<SpriteContents.AnimationState> animatedTexturesStates;
    @Shadow private int width;
    @Shadow private int height;

    @Inject(method = "upload", at = @At("TAIL"))
    private void combatant$buildMaterialAtlases(SpriteLoader.Preparations preparations, CallbackInfo ci) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        MaterialAtlasManager.global().rebuild(sprites, animatedTexturesStates, width, height);
    }

    @Inject(method = "cycleAnimationFrames", at = @At("TAIL"))
    private void combatant$syncMaterialAnimations(CallbackInfo ci) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        MaterialAtlasManager.global().synchronizeAnimations();
    }

    @Inject(method = "clearTextureData", at = @At("HEAD"))
    private void combatant$releaseMaterialAtlases(CallbackInfo ci) {
        if (!TextureAtlas.LOCATION_BLOCKS.equals(location)) return;
        MaterialAtlasManager.global().release();
    }
}
