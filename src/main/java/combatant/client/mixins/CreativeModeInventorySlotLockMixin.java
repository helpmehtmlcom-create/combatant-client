/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.util.player.inventory.InventoryComponentMap;
import combatant.client.util.player.inventory.InventorySlotLocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creative handles slot clicks itself rather than always dispatching to the menu click handler. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventorySlotLockMixin {
    @Shadow private Slot destroyItemSlot;

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void combatant$protectCreativeInventory(Slot slot, int slotId, int button,
                                                    ContainerInput input, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int realIndex = InventoryComponentMap.playerInventoryIndex(slot, player);
        if (realIndex >= 0 && InventorySlotLocks.blocks(realIndex, null)) {
            ci.cancel();
            return;
        }
        if (input == ContainerInput.SWAP
                && ((button == InventorySlotLocks.OFFHAND && InventorySlotLocks.blocks(InventorySlotLocks.OFFHAND, null))
                || (button >= 0 && button < 9 && InventorySlotLocks.blocks(button, null)))) {
            ci.cancel();
            return;
        }
        // Creative's destroy slot + shift-click clears the entire player inventory.
        if (slot == destroyItemSlot && input == ContainerInput.QUICK_MOVE
                && InventorySlotLocks.hasLockedPlayerSlot()) {
            ci.cancel();
        }
    }
}
