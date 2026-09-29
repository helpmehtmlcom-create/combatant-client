/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.compat.iris.gui;

import net.irisshaders.iris.gui.element.IrisElementRow;
import net.minecraft.network.chat.Component;

public interface IrisHeaderEntryAccess {
    Component combatant$getText();
    IrisElementRow combatant$getBackButton();
    IrisElementRow.TextButtonElement combatant$getResetButton();
    IrisElementRow.IconButtonElement combatant$getImportButton();
    IrisElementRow.IconButtonElement combatant$getExportButton();
}
