/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.CategoryRules;
import combatant.client.features.relations.CategoryType;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.RotationUtil;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.combat.AttackUtil;
import combatant.client.util.player.PlayerHealthResolver;
import combatant.client.util.player.ResetAttackCooldown;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.target.TargetManager;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Automatically prioritizes rapid melee strikes or crystal detonations to pop enemy totems
 * when target health falls below a configured threshold before they can heal or retreat.
 */
@ModuleInfo(
        id = "antitotem",
        displayName = "AntiTotem",
        aliases = {"totempopper", "predictpop"},
        category = ModuleCategory.COMBAT,
        description = "Prioritizes rapid attacks or crystal detonations to pop enemy totems before they heal."
)
public class AntiTotem extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Float> healthThreshold =
            num("antitotem_health_threshold", "healthThreshold", 6.0f, 1.0f, 12.0f);

    private final BooleanValue predictDamage =
            bool("antitotem_predict_damage", "predictDamage", true);

    private final EnumValue<AntiTotemMode> mode =
            enumSetting("antitotem_mode", "mode", AntiTotemMode.BOTH, AntiTotemMode.values());

    private final NumberValue<Float> range =
            num("antitotem_range", "range", 6.0f, 2.0f, 10.0f);

    private final BooleanValue packetAttack =
            bool("antitotem_packet_attack", "packetAttack", true);

    private final BooleanValue autoSwitch =
            bool("antitotem_auto_switch", "autoSwitch", true);

    private final BooleanValue rotate =
            bool("antitotem_rotate", "rotate", true);

    private LivingEntity targetedEntity;

    @Override
    public void onDisable() {
        if (targetedEntity != null) {
            TargetManager.setForcedTarget(null);
            TargetManager.setAutoCrystalTarget(null);
            targetedEntity = null;
        }
    }

    @EventHandler(priority = 30)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.gameMode == null || mc.getConnection() == null) {
            return;
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }

        LivingEntity target = findTarget(player, level, range.get());
        if (target == null) {
            if (targetedEntity != null) {
                TargetManager.setForcedTarget(null);
                TargetManager.setAutoCrystalTarget(null);
                targetedEntity = null;
            }
            return;
        }

        PlayerHealthResolver.HealthSnapshot snapshot = PlayerHealthResolver.resolve(target);
        float currentHealth = snapshot.totalHealth();
        float estimatedRemainingHealth = currentHealth;

        if (predictDamage.get()) {
            float predictedDmg = calculatePredictedDamage(player, target);
            estimatedRemainingHealth = Math.max(0.0f, currentHealth - predictedDmg);
        }

        if (estimatedRemainingHealth <= healthThreshold.get()) {
            targetedEntity = target;

            // 1. Signal combat modules to prioritize popping target
            TargetManager.setForcedTarget(target);
            TargetManager.setAutoCrystalTarget(target);
            TargetManager.setModuleTarget(target);

            AntiTotemMode activeMode = mode.get();

            // 2. Prioritize crystal detonation if in CRYSTAL or BOTH mode
            if (activeMode == AntiTotemMode.CRYSTAL || activeMode == AntiTotemMode.BOTH) {
                EndCrystal bestCrystal = findBestDetonationCrystal(player, target);
                if (bestCrystal != null) {
                    detonateCrystal(player, bestCrystal);
                }
            }

            // 3. Prioritize rapid attack execution if in SWORD or BOTH mode
            if (activeMode == AntiTotemMode.SWORD || activeMode == AntiTotemMode.BOTH) {
                if (player.distanceTo(target) <= 4.0f) {
                    rapidAttack(player, target);
                }
            }
        } else if (targetedEntity == target) {
            TargetManager.setForcedTarget(null);
            TargetManager.setAutoCrystalTarget(null);
            targetedEntity = null;
        }
    }

    private LivingEntity findTarget(LocalPlayer player, Level level, float maxRange) {
        LivingEntity managerTarget = TargetManager.getTarget();
        if (isValidTarget(player, managerTarget, maxRange)) {
            return managerTarget;
        }

        LivingEntity best = null;
        float lowestHealth = Float.MAX_VALUE;
        double closestDistSq = maxRange * maxRange;

        AABB searchBox = player.getBoundingBox().inflate(maxRange);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, searchBox, e -> isValidTarget(player, e, maxRange));

        for (LivingEntity candidate : entities) {
            PlayerHealthResolver.HealthSnapshot snapshot = PlayerHealthResolver.resolve(candidate);
            float hp = snapshot.totalHealth();
            double distSq = player.distanceToSqr(candidate);

            if (hp < lowestHealth || (Math.abs(hp - lowestHealth) < 0.5f && distSq < closestDistSq)) {
                best = candidate;
                lowestHealth = hp;
                closestDistSq = distSq;
            }
        }

        return best;
    }

    private boolean isValidTarget(LocalPlayer player, LivingEntity target, float maxRange) {
        if (target == null || target == player) return false;
        if (!target.isAlive() || target.isRemoved()) return false;
        if (player.distanceTo(target) > maxRange) return false;

        // Target must hold Totem of Undying in offhand
        if (!target.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }

        if (target instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            CategoryType type = CategoryRules.determine(p.getGameProfile().name());
            if (type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF) {
                return false;
            }
        }

        return true;
    }

    private float calculatePredictedDamage(LocalPlayer player, LivingEntity target) {
        float maxCrystalDmg = 0.0f;
        AntiTotemMode activeMode = mode.get();

        if (activeMode == AntiTotemMode.CRYSTAL || activeMode == AntiTotemMode.BOTH) {
            AABB searchBox = target.getBoundingBox().inflate(8.0);
            List<EndCrystal> crystals = target.level().getEntitiesOfClass(EndCrystal.class, searchBox, Entity::isAlive);
            for (EndCrystal crystal : crystals) {
                float dmg = ExplosionDamageUtil.getCrystalDamage(target, crystal.position(), 1, false);
                if (dmg > maxCrystalDmg) {
                    maxCrystalDmg = dmg;
                }
            }
        }

        float meleeDmg = 0.0f;
        if (activeMode == AntiTotemMode.SWORD || activeMode == AntiTotemMode.BOTH) {
            if (player.distanceTo(target) <= 4.2f) {
                meleeDmg = estimateMeleeDamage(player, target);
            }
        }

        return Math.max(maxCrystalDmg, meleeDmg);
    }

    private float estimateMeleeDamage(LocalPlayer player, LivingEntity target) {
        double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float cooldown = player.getAttackStrengthScale(0.5f);
        float damage = (float) (baseDamage * (0.2f + cooldown * cooldown * 0.8f));

        if (player.fallDistance > 0.0f && !player.onGround() && !player.isInWater()) {
            damage *= 1.5f;
        }

        int armor = target.getArmorValue();
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        return CombatRules.getDamageAfterAbsorb(target, damage, target.damageSources().playerAttack(player), (float) armor, (float) toughness);
    }

    private EndCrystal findBestDetonationCrystal(LocalPlayer player, LivingEntity target) {
        AABB searchBox = player.getBoundingBox().inflate(6.0);
        List<EndCrystal> crystals = player.level().getEntitiesOfClass(EndCrystal.class, searchBox, Entity::isAlive);

        EndCrystal best = null;
        float highestDamage = 0.0f;

        for (EndCrystal crystal : crystals) {
            if (player.distanceTo(crystal) > 5.5f) continue;
            if (crystal.distanceTo(target) > 8.0f) continue;

            float damage = ExplosionDamageUtil.getCrystalDamage(target, crystal.position(), 1, false);
            if (damage > highestDamage) {
                highestDamage = damage;
                best = crystal;
            }
        }

        return best;
    }

    private void detonateCrystal(LocalPlayer player, EndCrystal crystal) {
        if (crystal == null || !crystal.isAlive() || crystal.isRemoved()) return;

        if (rotate.get()) {
            float[] rotations = RotationUtil.getRotationsToEntity(player, crystal);
            RotationTarget rotTarget = new RotationTarget(
                    new Rotation(rotations[0], rotations[1], false),
                    crystal,
                    List.of(),
                    1,
                    4.0f,
                    true,
                    MovementCorrection.SILENT,
                    null
            );
            RotationManager.INSTANCE.setRotationTarget(rotTarget, 32, this);
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        rotations[0],
                        rotations[1],
                        player.onGround(),
                        player.horizontalCollision
                ));
            }
        }

        if (packetAttack.get() && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundInteractPacket(
                    crystal.getId(),
                    null,
                    null,
                    player.isShiftKeyDown()
            ));
        }

        if (mc.gameMode != null) {
            mc.gameMode.attack(player, crystal);
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    private void rapidAttack(LocalPlayer player, LivingEntity target) {
        if (autoSwitch.get()) {
            int weaponSlot = findBestWeaponSlot(player);
            if (weaponSlot != -1 && weaponSlot != InventorySwap.INSTANCE.clientSelectedSlot()) {
                InventorySwap.INSTANCE.leaseHotbar(this, weaponSlot, 2);
            }
        }

        if (rotate.get()) {
            float[] rotations = RotationUtil.getRotationsToEntity(player, target);
            RotationTarget rotTarget = new RotationTarget(
                    new Rotation(rotations[0], rotations[1], false),
                    target,
                    List.of(),
                    1,
                    4.0f,
                    true,
                    MovementCorrection.SILENT,
                    null
            );
            RotationManager.INSTANCE.setRotationTarget(rotTarget, 32, this);
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        rotations[0],
                        rotations[1],
                        player.onGround(),
                        player.horizontalCollision
                ));
            }
        }

        if (packetAttack.get()) {
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundInteractPacket(
                        target.getId(),
                        null,
                        null,
                        player.isShiftKeyDown()
                ));
            }
            player.swing(InteractionHand.MAIN_HAND);
            ResetAttackCooldown.resetAttackCooldown(player);
        } else {
            AttackUtil.attack(mc, target);
        }
    }

    private int findBestWeaponSlot(LocalPlayer player) {
        int current = InventorySwap.INSTANCE.clientSelectedSlot();
        if (current >= 0 && current < 9) {
            ItemStack stack = player.getInventory().getItem(current);
            if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
                return current;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
                return i;
            }
        }
        return -1;
    }

    public enum AntiTotemMode {
        CRYSTAL,
        SWORD,
        BOTH
    }
}
