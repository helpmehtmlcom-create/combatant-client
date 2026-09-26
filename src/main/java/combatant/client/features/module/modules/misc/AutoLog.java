/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "autolog",
        displayName = "AutoLog",
        aliases = {"AutoDisconnect", "SafeDisconnect"},
        category = ModuleCategory.MISC,
        description = "Automatically disconnects from the server when health is low or danger is detected."
)
public final class AutoLog extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            num("autoLogHealthThreshold", "health_threshold", 6.0f, 1.0f, 20.0f);

    private final BooleanValue checkTotem =
            bool("autoLogCheckTotem", "check_totem", true);

    private final BooleanValue onTotemZero =
            bool("autoLogOnTotemZero", "on_totem_zero", false);

    private final BooleanValue onCrystalNear =
            bool("autoLogOnCrystalNear", "on_crystal_near", false);

    private final NumberValue<Float> crystalDistance =
            visibleWhen(num("autoLogCrystalDistance", "crystal_distance", 4.5f, 2.0f, 8.0f), onCrystalNear::get);

    private final BooleanValue autoDisable =
            bool("autoLogAutoDisable", "auto_disable", true);

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null || mc.level == null) {
            return;
        }

        float totalHealth = player.getHealth() + player.getAbsorptionAmount();
        int totems = countTotems(player);

        if (onTotemZero.get() && totems == 0) {
            disconnect(String.format("[Combatant AutoLog] Disconnected: 0 Totems remaining (Health: %.1f)", totalHealth));
            return;
        }

        if (totalHealth <= healthThreshold.get()) {
            if (!checkTotem.get() || totems == 0) {
                disconnect(String.format("[Combatant AutoLog] Disconnected: Low Health (%.1f HP, %d Totems)", totalHealth, totems));
                return;
            }
        }

        if (onCrystalNear.get()) {
            double dist = getClosestCrystalDistance(player);
            if (dist <= crystalDistance.get() && totalHealth <= healthThreshold.get() * 1.5f) {
                disconnect(String.format("[Combatant AutoLog] Disconnected: Crystal Danger (dist: %.1f, HP: %.1f)", dist, totalHealth));
            }
        }
    }

    private void disconnect(String reason) {
        if (autoDisable.get()) {
            setEnabled(false);
        }

        if (mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal(reason));
        }
    }

    private int countTotems(LocalPlayer player) {
        if (player == null) return 0;
        int count = 0;
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            count += player.getOffhandItem().getCount();
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private double getClosestCrystalDistance(LocalPlayer player) {
        if (player == null || mc.level == null) {
            return Double.MAX_VALUE;
        }

        double minDist = Double.MAX_VALUE;
        double range = crystalDistance.get();

        for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                EndCrystal.class,
                player.getBoundingBox().inflate(range),
                c -> c != null && !c.isRemoved()
        )) {
            double dist = player.distanceTo(crystal);
            if (dist < minDist) {
                minDist = dist;
            }
        }

        return minDist;
    }
}
