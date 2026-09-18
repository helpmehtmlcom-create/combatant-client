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
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.features.relations.CategoryService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "autoshield",
        displayName = "AutoShield",
        description = "Automatically raises shield against incoming projectiles, explosive crystals, and melee attacks.",
        category = ModuleCategory.COMBAT,
        subCategory = ModuleSubCategory.DEFENSE,
        aliases = {"shieldassist", "smartshield", "blockerdefense"}
)
public class AutoShield extends Module {

    private final BooleanValue projectiles =
            bool("autoshield_projectiles", "projectiles", true);
    private final BooleanValue crystalDefense =
            bool("autoshield_crystals", "crystals", true);
    private final BooleanValue meleeHits =
            bool("autoshield_melee", "melee", true);
    private final BooleanValue antiAxeStun =
            bool("autoshield_anti_axe", "anti_axe", true);
    private final NumberValue<Double> defenseRange =
            num("autoshield_range", "range", 4.0, 2.0, 7.0);

    private final Minecraft mc = Minecraft.getInstance();
    private boolean isBlocking = false;

    @Override
    public void onDisable() {
        stopBlocking();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        InteractionHand shieldHand = getShieldHand(player);
        if (shieldHand == null) {
            stopBlocking();
            return;
        }

        boolean threatDetected = checkThreats(player);

        if (threatDetected) {
            startBlocking(player, shieldHand);
        } else {
            stopBlocking();
        }
    }

    private boolean checkThreats(LocalPlayer player) {
        Vec3 playerPos = player.position();
        double rangeSq = defenseRange.get() * defenseRange.get();

        // 1. Check for incoming projectiles
        if (projectiles.get()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof Projectile projectile && projectile.isAlive()) {
                    if (projectile.getOwner() == player) continue;
                    if (projectile.position().distanceToSqr(playerPos) <= 16.0 * 16.0) {
                        Vec3 motion = projectile.getDeltaMovement();
                        Vec3 toPlayer = player.getEyePosition().subtract(projectile.position());
                        if (motion.dot(toPlayer) > 0.1) {
                            return true;
                        }
                    }
                }
            }
        }

        // 2. Check for nearby crystals
        if (crystalDefense.get()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof EndCrystal crystal && crystal.isAlive()) {
                    if (crystal.distanceToSqr(player) <= 36.0) {
                        return true;
                    }
                }
            }
        }

        // 3. Check for nearby melee opponents
        if (meleeHits.get()) {
            for (Player other : mc.level.players()) {
                if (other == player || !other.isAlive() || other.isSpectator()) continue;
                if (CategoryService.isFriend(other)) continue;

                double distSq = other.distanceToSqr(player);
                if (distSq <= rangeSq) {
                    if (antiAxeStun.get() && other.getMainHandItem().is(ItemTags.AXES)) {
                        // Drop shield to avoid 5-second axe stun
                        return false;
                    }
                    return true;
                }
            }
        }

        return false;
    }

    private void startBlocking(LocalPlayer player, InteractionHand hand) {
        if (!isBlocking) {
            isBlocking = true;
            mc.options.keyUse.setDown(true);
        }
    }

    private void stopBlocking() {
        if (isBlocking) {
            isBlocking = false;
            mc.options.keyUse.setDown(false);
        }
    }

    private InteractionHand getShieldHand(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.SHIELD)) return InteractionHand.OFF_HAND;
        if (player.getMainHandItem().is(Items.SHIELD)) return InteractionHand.MAIN_HAND;
        return null;
    }
}
