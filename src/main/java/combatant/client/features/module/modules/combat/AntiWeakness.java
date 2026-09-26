/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;

@ModuleInfo(
        id = "antiweakness",
        displayName = "AntiWeakness",
        aliases = {"WeaknessSwap", "AntiWeak"},
        category = ModuleCategory.COMBAT,
        description = "Automatically swaps to a weapon when attacking or breaking crystals while affected by Weakness."
)
public final class AntiWeakness extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("antiWeaknessMode", "mode", Mode.SILENT, Mode.values());

    private final BooleanValue swapBack =
            bool("antiWeaknessSwapBack", "swap_back", true);

    private final BooleanValue crystalsOnly =
            bool("antiWeaknessCrystalsOnly", "crystals_only", true);

    private int lastSwapBackSlot = -1;
    private int swapBackTicks = 0;

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        lastSwapBackSlot = -1;
        swapBackTicks = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (swapBackTicks > 0) {
            swapBackTicks--;
            if (swapBackTicks == 0 && lastSwapBackSlot >= 0 && lastSwapBackSlot < 9) {
                InventorySwap.INSTANCE.selectHotbar(lastSwapBackSlot);
                lastSwapBackSlot = -1;
            }
        }
    }

    @EventHandler(priority = 100)
    public void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || event.getPlayer() != player) return;
        if (!player.hasEffect(MobEffects.WEAKNESS)) return;

        Entity target = event.getTarget();
        if (crystalsOnly.get() && !(target instanceof EndCrystal)) {
            return;
        }

        performWeaknessSwap(player);
    }

    @EventHandler(priority = 100)
    public void onPacketSend(PacketEvent.Send event) {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || !player.hasEffect(MobEffects.WEAKNESS)) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundInteractPacket interactPacket && isAttack(interactPacket)) {
            if (mc.level != null) {
                Entity target = mc.level.getEntity(interactPacket.entityId());
                if (crystalsOnly.get() && !(target instanceof EndCrystal)) {
                    return;
                }
                performWeaknessSwap(player);
            }
        }
    }

    private void performWeaknessSwap(LocalPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (isWeapon(held)) {
            return;
        }

        int weaponSlot = findWeaponSlot(player);
        if (weaponSlot == -1) return;

        if (mode.get() == Mode.SILENT) {
            InventorySwap.INSTANCE.leaseHotbar(this, weaponSlot, 2);
        } else {
            int currentSlot = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
            InventorySwap.INSTANCE.selectHotbar(weaponSlot);
            if (swapBack.get()) {
                lastSwapBackSlot = currentSlot;
                swapBackTicks = 2;
            }
        }
    }

    private boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES);
    }

    private int findWeaponSlot(LocalPlayer player) {
        if (player == null) return -1;

        // Prefer sword
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.SWORDS)) {
                return i;
            }
        }

        // Then axe
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.AXES)) {
                return i;
            }
        }

        // Then pickaxe
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.PICKAXES)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isAttack(ServerboundInteractPacket packet) {
        return packet.hand() == null && packet.location() == null;
    }

    public enum Mode {
        SILENT,
        SWAP
    }
}
