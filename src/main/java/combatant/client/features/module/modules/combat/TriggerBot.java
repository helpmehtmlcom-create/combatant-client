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
import combatant.client.features.relations.PlayerRelations;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

@ModuleInfo(
        id = "triggerbot",
        displayName = "TriggerBot",
        aliases = {"AutoHit", "CrosshairTrigger"},
        category = ModuleCategory.COMBAT,
        description = "Automatically attacks entities that pass under your crosshair"
)
public final class TriggerBot extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue cooldown =
            bool("triggerBotCooldown", "cooldown", true);

    private final BooleanValue weaponsOnly =
            bool("triggerBotWeaponsOnly", "weapons_only", true);

    private final BooleanValue playersOnly =
            bool("triggerBotPlayersOnly", "players_only", true);

    private final BooleanValue ignoreFriends =
            bool("triggerBotIgnoreFriends", "ignore_friends", true);

    private final NumberValue<Integer> delayMs =
            num("triggerBotDelayMs", "delay_ms", 50, 0, 500);

    private long lastAttackTime = 0L;

    public TriggerBot() {
        super();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || ClientScreen.current() != null) {
            return;
        }

        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof LivingEntity living) || !living.isAlive() || living == player) {
            return;
        }

        if (playersOnly.get() && !(living instanceof Player)) {
            return;
        }

        if (living instanceof Player targetPlayer && ignoreFriends.get()) {
            String name = targetPlayer.getGameProfile().name();
            if (PlayerRelations.get().isFriend(name)) {
                return;
            }
        }

        if (weaponsOnly.get() && !isHoldingWeapon(player)) {
            return;
        }

        if (cooldown.get() && player.getAttackStrengthScale(0.5f) < 0.95f) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAttackTime < delayMs.get()) {
            return;
        }

        lastAttackTime = now;
        mc.gameMode.attack(player, living);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean isHoldingWeapon(LocalPlayer player) {
        var item = player.getMainHandItem();
        return item.is(ItemTags.SWORDS) || item.is(ItemTags.AXES) || item.is(ItemTags.MACE_ENCHANTABLE);
    }
}
