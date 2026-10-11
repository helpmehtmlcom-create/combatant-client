/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.accessors;

import combatant.client.util.player.inventory.InventoryComponentMap;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Creative inventory slots delegate to a target but expose a different container index. */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotWrapperAccessor extends InventoryComponentMap.DelegatingSlot {
    @Override
    @Accessor("target")
    Slot combatant$backingSlot();
}
