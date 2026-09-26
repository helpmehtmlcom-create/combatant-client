/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RestrictedSingleUseAction;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@ModuleInfo(
        id = "packetexp",
        displayName = "PacketExp",
        aliases = {"FastExp", "AutoExp"},
        category = ModuleCategory.PLAYER,
        description = "Throws experience bottles at ultra-high speed using packet interactions."
)
public final class PacketExp extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> packetsPerTick =
            num("packetExpPacketsPerTick", "packets_per_tick", 2, 1, 10);

    private final BooleanValue silentPitch =
            bool("packetExpSilentPitch", "silent_pitch", true);

    private final NumberValue<Float> pitch =
            visibleWhen(num("packetExpPitch", "pitch", 90.0f, 70.0f, 90.0f), silentPitch::get);

    private final BooleanValue autoDisable =
            bool("packetExpAutoDisable", "auto_disable", true);

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.release(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;

        boolean offhandXp = player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
        int hotbarSlot = offhandXp ? -1 : findXpSlot(player);

        if (!offhandXp && hotbarSlot == -1) {
            if (autoDisable.get()) {
                setEnabled(false);
            }
            return;
        }

        int count = packetsPerTick.get();

        if (offhandXp) {
            for (int i = 0; i < count; i++) {
                if (!player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) break;
                mc.gameMode.useItem(player, InteractionHand.OFF_HAND);
                player.swing(InteractionHand.OFF_HAND);
            }
        } else {
            InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 2);
            for (int i = 0; i < count; i++) {
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
                player.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    @EventHandler(priority = 20)
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled() || !silentPitch.get() || mc.player == null) return;
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;

        LocalPlayer player = mc.player;
        RotationTarget target = new RotationTarget(
                new Rotation(player.getYRot(), pitch.get()),
                player,
                List.of(),
                1,
                4.0f,
                false,
                MovementCorrection.SILENT,
                new RestrictedSingleUseAction(null)
        );
        RotationManager.INSTANCE.setRotationTarget(target, 50, this);
    }

    private int findXpSlot(LocalPlayer player) {
        if (player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.EXPERIENCE_BOTTLE)) {
                return i;
            }
        }
        return -1;
    }
}
