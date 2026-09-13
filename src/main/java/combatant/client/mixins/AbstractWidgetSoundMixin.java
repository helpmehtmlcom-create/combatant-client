/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins;

import combatant.client.features.gui.clickgui.sound.GuiSound;
import combatant.client.features.gui.hud.nondraggable.impl.BetterButtons;
import combatant.client.runtime.RuntimeGate;
import combatant.client.util.sound.SoundSystem;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces vanilla widget press feedback while Combatant runtime features are active. */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetSoundMixin {
    @Inject(method = "playDownSound", at = @At("HEAD"), cancellable = true)
    private void combatant$replaceVanillaButtonSound(SoundManager soundManager, CallbackInfo ci) {
        if (!RuntimeGate.canRunModules()) return;
        if (!BetterButtons.get().useCustomButtonSounds()) return;
        if (!SoundSystem.get().isReady()) return;
        GuiSound.BUTTON.feedback(0.65);
        ci.cancel();
    }
}
