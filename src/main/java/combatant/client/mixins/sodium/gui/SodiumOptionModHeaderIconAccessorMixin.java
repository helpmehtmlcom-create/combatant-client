/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumModIconAccess;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.gui.ColorTheme;
import net.caffeinemc.mods.sodium.client.gui.options.control.AbstractOptionList;
import net.caffeinemc.mods.sodium.client.util.Dim2i;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.caffeinemc.mods.sodium.client.gui.widgets.OptionListWidget$ModHeaderWidget", remap = false)
public abstract class SodiumOptionModHeaderIconAccessorMixin implements SodiumModIconAccess {
    @Shadow @Final Identifier icon;
    @Shadow @Final boolean iconMonochrome;
    @Unique private ModOptions combatant$modOptions;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void combatant$captureModOptions(AbstractOptionList optionList,
                                             Dim2i dimensions,
                                             ModOptions modOptions,
                                             ColorTheme theme,
                                             CallbackInfo ci) {
        this.combatant$modOptions = modOptions;
    }

    @Override
    public Identifier combatant$getIcon() {
        return icon;
    }

    @Override
    public boolean combatant$isIconMonochrome() {
        return iconMonochrome;
    }

    @Override
    public ModOptions combatant$getModOptions() {
        return combatant$modOptions;
    }
}
