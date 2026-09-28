/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.sodium.gui;

import combatant.client.compat.sodium.gui.SodiumPromptAccess;
import net.caffeinemc.mods.sodium.client.gui.prompt.ScreenPrompt;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = ScreenPrompt.class, remap = false)
public interface SodiumPromptAccessorMixin extends SodiumPromptAccess {
    @Accessor("text")
    @Override List<FormattedText> combatant$getText();

    @Accessor("width")
    @Override int combatant$getWidth();

    @Accessor("height")
    @Override int combatant$getHeight();
}
