/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisHeaderEntryAccess;
import net.irisshaders.iris.gui.element.IrisElementRow;
import net.irisshaders.iris.gui.element.ShaderPackOptionList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ShaderPackOptionList.HeaderEntry.class, remap = false)
public interface IrisHeaderEntryAccessorMixin extends IrisHeaderEntryAccess {
    @Accessor("text")
    @Override Component combatant$getText();

    @Accessor("backButton")
    @Override IrisElementRow combatant$getBackButton();

    @Accessor("resetButton")
    @Override IrisElementRow.TextButtonElement combatant$getResetButton();

    @Accessor("importButton")
    @Override IrisElementRow.IconButtonElement combatant$getImportButton();

    @Accessor("exportButton")
    @Override IrisElementRow.IconButtonElement combatant$getExportButton();
}
