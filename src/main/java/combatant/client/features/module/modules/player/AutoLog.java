/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.world.ExplosionDamageUtil;

@ModuleInfo(
        id = "autolog",
        displayName = "AutoLog",
        category = ModuleCategory.PLAYER,
        description = "Automatically disconnects safely before fatal crystal damage, low health, or when out of totems."
)
public final class AutoLog extends Module {

    private final NumberValue<Double> health =
            num("health_threshold", "health", 6.0, 1.0, 20.0);
    private final NumberValue<Integer> totemThreshold =
            num("totem_threshold", "totems", 0, 0, 10);
    private final BooleanValue crystalDanger =
            bool("crystal_danger", "crystal_danger", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.getConnection() == null || mc.level == null) return;
        LocalPlayer player = mc.player;
        if (!player.isAlive()) return;

        int totems = countTotems(player);
        float currentHp = player.getHealth() + player.getAbsorptionAmount();

        // 1. Crystal danger check: Predict fatal crystal explosions before they deal damage
        if (crystalDanger.get()) {
            for (EndCrystal crystal : mc.level.getEntitiesOfClass(
                    EndCrystal.class,
                    player.getBoundingBox().inflate(10.0),
                    c -> c != null && !c.isRemoved()
            )) {
                float damage = ExplosionDamageUtil.calculateCrystalDamage(crystal.position(), player);
                if (damage >= currentHp) {
                    if (totems <= totemThreshold.get() || damage >= currentHp + (totems <= 1 ? 0.0f : 40.0f)) {
                        disconnect(String.format("[AutoLog] Fatal crystal danger (Damage: %.1f, HP: %.1f, Totems: %d)",
                                damage, currentHp, totems));
                        return;
                    }
                }
            }
        }

        // 2. Health threshold & totem depletion check
        if (player.getHealth() <= health.get() && totems <= totemThreshold.get()) {
            disconnect(String.format("[AutoLog] Low health safety trigger (HP: %.1f, Totems: %d)",
                    player.getHealth(), totems));
        }
    }

    private int countTotems(LocalPlayer player) {
        int count = 0;
        if (player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            count += player.getOffhandItem().getCount();
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void disconnect(String reason) {
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal(reason));
        }
        setEnabled(false);
    }
}
