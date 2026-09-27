/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import java.util.concurrent.ThreadLocalRandom;

@ModuleInfo(
        id = "autoclicker",
        displayName = "AutoClicker",
        aliases = {"FastClicker", "SpamClicker"},
        category = ModuleCategory.COMBAT,
        description = "Automatically clicks when holding mouse buttons with randomized CPS."
)
public final class AutoClicker extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue leftClick =
            bool("autoClickerLeftClick", "left_click", true);

    private final BooleanValue rightClick =
            bool("autoClickerRightClick", "right_click", false);

    private final NumberValue<Integer> minCps =
            num("autoClickerMinCps", "min_cps", 9, 1, 20);

    private final NumberValue<Integer> maxCps =
            num("autoClickerMaxCps", "max_cps", 13, 1, 20);

    private final BooleanValue weaponsOnly =
            bool("autoClickerWeaponsOnly", "weapons_only", true);

    private long lastLeftClickTime = 0L;
    private long leftDelayMs = 100L;

    private long lastRightClickTime = 0L;
    private long rightDelayMs = 100L;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || ClientScreen.current() != null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (leftClick.get() && mc.options.keyAttack.isDown()) {
            if (isWeaponHeld(player)) {
                if (now - lastLeftClickTime >= leftDelayMs) {
                    lastLeftClickTime = now;
                    leftDelayMs = nextRandomDelay();

                    Entity target = mc.crosshairPickEntity;
                    if (target != null && target.isAlive()) {
                        mc.gameMode.attack(player, target);
                    }
                    player.swing(InteractionHand.MAIN_HAND);
                }
            }
        }

        if (rightClick.get() && mc.options.keyUse.isDown()) {
            if (now - lastRightClickTime >= rightDelayMs) {
                lastRightClickTime = now;
                rightDelayMs = nextRandomDelay();

                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
        }
    }

    private boolean isWeaponHeld(LocalPlayer player) {
        if (!weaponsOnly.get()) return true;
        return player.getMainHandItem().is(ItemTags.SWORDS) || player.getMainHandItem().is(ItemTags.AXES);
    }

    private long nextRandomDelay() {
        int min = Math.min(minCps.get(), maxCps.get());
        int max = Math.max(minCps.get(), maxCps.get());
        int cps = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        return 1000L / Math.max(1, cps);
    }
}
