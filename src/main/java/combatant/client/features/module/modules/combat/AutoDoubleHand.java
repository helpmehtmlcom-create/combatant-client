/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "autodoublehand",
        displayName = "Auto Double Hand",
        description = "Automatically equips a Totem of Undying to your main hand during critical combat moments (pop, low health, nearby crystals).",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.DEFENSE,
        aliases = {"doublehand", "doubletotem", "mainhandtotem"}
)
public class AutoDoubleHand extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue onPop =
            bool("doublehand_on_pop", "on_pop", true);
    private final BooleanValue onHealth =
            bool("doublehand_on_health", "on_health", true);
    private final NumberValue<Double> healthThreshold =
            num("doublehand_health_threshold", "health_threshold", 8.0, 1.0, 20.0);
    private final BooleanValue checkCrystals =
            bool("doublehand_check_crystals", "check_crystals", true);
    private final NumberValue<Double> crystalDistance =
            num("doublehand_crystal_dist", "crystal_distance", 5.0, 1.0, 10.0);

    private int originalSlot = -1;
    private int holdTicks = 0;

    @Override
    public void onDisable() {
        if (originalSlot >= 0 && mc.player != null) {
            InventorySwap.INSTANCE.selectHotbar(originalSlot);
        }
        originalSlot = -1;
        holdTicks = 0;
    }

    @EventHandler
    public void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            // Event ID 35 is totem pop
            if (packet.getEventId() == 35 && packet.getEntity(mc.level) == mc.player && onPop.get()) {
                equipMainhandTotem(20);
            }
        }
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (holdTicks > 0) {
            holdTicks--;
            if (holdTicks == 0 && originalSlot >= 0) {
                InventorySwap.INSTANCE.selectHotbar(originalSlot);
                originalSlot = -1;
            }
            return;
        }

        // Check health
        if (onHealth.get() && mc.player.getHealth() <= healthThreshold.get()) {
            equipMainhandTotem(10);
            return;
        }

        // Check nearby end crystals
        if (checkCrystals.get()) {
            double distSq = crystalDistance.get() * crystalDistance.get();
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(EndCrystal.class, mc.player.getBoundingBox().inflate(crystalDistance.get()))) {
                if (crystal.distanceToSqr(mc.player) <= distSq) {
                    equipMainhandTotem(5);
                    return;
                }
            }
        }
    }

    private void equipMainhandTotem(int ticks) {
        if (mc.player == null) return;
        if (mc.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
            holdTicks = Math.max(holdTicks, ticks);
            return;
        }

        int totemSlot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                totemSlot = i;
                break;
            }
        }

        if (totemSlot >= 0) {
            if (originalSlot < 0) {
                originalSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
            }
            InventorySwap.INSTANCE.selectHotbar(totemSlot);
            holdTicks = ticks;
        }
    }
}
