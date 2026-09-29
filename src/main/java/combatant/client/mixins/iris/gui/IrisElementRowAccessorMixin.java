/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.iris.gui;

import combatant.client.compat.iris.gui.IrisElementRowAccess;
import net.irisshaders.iris.gui.element.IrisElementRow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = IrisElementRow.class, remap = false)
public interface IrisElementRowAccessorMixin extends IrisElementRowAccess {
    @Accessor("orderedElements")
    @Override List<IrisElementRow.Element> combatant$getOrderedElements();
}
