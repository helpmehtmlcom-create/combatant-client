/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.PlayerMoveEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.ClientboundExplodePacketAccessor;
import combatant.client.mixins.accessors.ClientboundSetEntityMotionPacketAccessor;
import combatant.client.util.world.ExplosionDamageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * AnchorFly (ExplosionFly) - High-speed anarchy travel technique utilizing controlled blast knockback
 * (or wind burst / mace kinetic bursts) to propel the player forward at extreme velocities.
 */
@ModuleInfo(
        id = "anchorfly",
        displayName = "AnchorFly",
        category = ModuleCategory.MOVEMENT,
        description = "Propels player forward at extreme velocities using controlled explosion and blast knockback."
)
public final class AnchorFly extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> boostSpeed =
            num("Boost Speed", "boostSpeed", 2.5, 1.0, 5.0);

    private final NumberValue<Double> verticalBoost =
            num("Vertical Boost", "verticalBoost", 0.5, 0.0, 2.0);

    private final NumberValue<Float> maxDamage =
            num("Max Damage", "maxDamage", 6.0f, 1.0f, 20.0f);

    private final BooleanValue requireElytra =
            bool("Require Elytra", "requireElytra", false);

    private long lastExplosionPacketTime = 0;
    private Vec3 lastExplosionCenter = null;
    private float lastExplosionRadius = 0.0f;
    private int glideTicksRemaining = 0;

    @Override
    public void onDisable() {
        glideTicksRemaining = 0;
        lastExplosionCenter = null;
        lastExplosionRadius = 0.0f;
    }
    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!isEnabled() || mc.player == null) return;

        Packet<?> packet = event.getPacket();

        // 1. ClientboundExplodePacket
        if (packet instanceof ClientboundExplodePacket explosion) {
            handleExplosionPacket(explosion);
            return;
        }

        // 2. ClientboundSetEntityMotionPacket
        if (packet instanceof ClientboundSetEntityMotionPacket velocity) {
            handleVelocityPacket(velocity);
            return;
        }

        // 3. ClientboundDamageEventPacket (track explosion/burst source)
        if (packet instanceof ClientboundDamageEventPacket damage && damage.entityId() == mc.player.getId()) {
            if (mc.level != null) {
                DamageSource source = damage.getSource(mc.level);
                if (source != null && (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_MACE_SMASH))) {
                    lastExplosionPacketTime = System.currentTimeMillis();
                    damage.sourcePosition().ifPresent(pos -> lastExplosionCenter = pos);
                }
            }
        }
    }

    private void handleExplosionPacket(ClientboundExplodePacket explosion) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        lastExplosionPacketTime = System.currentTimeMillis();
        lastExplosionCenter = explosion.center();
        lastExplosionRadius = explosion.radius();

        Optional<Vec3> playerKnockback = explosion.playerKnockback();
        if (playerKnockback.isEmpty()) return;

        Vec3 rawKb = playerKnockback.get();
        if (rawKb.lengthSqr() < 1e-4) return;

        // Damage threshold enforcement
        float estimatedDamage = 0.0f;
        if (mc.level != null) {
            estimatedDamage = ExplosionDamageUtil.getExplosionDamage(player, explosion.center(), explosion.radius(), 0, false);
        }

        if (estimatedDamage > maxDamage.get() || (player.getHealth() > 0 && estimatedDamage >= player.getHealth())) {
            // Nullify knockback to prevent unwanted/lethal fling
            ((ClientboundExplodePacketAccessor) (Object) explosion).combatant$setPlayerKnockback(Optional.empty());
            return;
        }

        // Elytra requirement check
        if (requireElytra.get() && !hasElytraEquipped(player)) {
            return;
        }

        // Calculate look-aligned impulse vector
        Vec3 boosted = calculateBoostVector(player, rawKb);

        // Intercept and scale packet knockback
        ((ClientboundExplodePacketAccessor) (Object) explosion).combatant$setPlayerKnockback(Optional.of(boosted));

        // Directly set delta movement for instant response
        player.setDeltaMovement(boosted);
        triggerBoost(player);
    }

    private void handleVelocityPacket(ClientboundSetEntityMotionPacket velocity) {
        LocalPlayer player = mc.player;
        if (player == null || velocity.id() != player.getId()) return;

        Vec3 rawMotion = velocity.movement();
        if (rawMotion.lengthSqr() < 0.05) return;

        // Verify if motion originates from blast / kinetic burst
        long now = System.currentTimeMillis();
        boolean isExplosionWindow = (now - lastExplosionPacketTime) <= 500;
        boolean isDamageWindow = player.hurtTime > 0;

        if (!isExplosionWindow && !isDamageWindow) {
            return;
        }

        // Damage threshold enforcement
        if (lastExplosionCenter != null && isExplosionWindow && mc.level != null) {
            float estimatedDamage = ExplosionDamageUtil.getExplosionDamage(player, lastExplosionCenter, Math.max(4.0f, lastExplosionRadius), 0, false);
            if (estimatedDamage > maxDamage.get() || (player.getHealth() > 0 && estimatedDamage >= player.getHealth())) {
                return;
            }
        }

        // Elytra requirement check
        if (requireElytra.get() && !hasElytraEquipped(player)) {
            return;
        }

        // Calculate look-aligned impulse vector
        Vec3 boosted = calculateBoostVector(player, rawMotion);

        // Intercept and scale velocity packet
        ((ClientboundSetEntityMotionPacketAccessor) (Object) velocity).combatant$setVelocity(boosted);

        // Directly set delta movement
        player.setDeltaMovement(boosted);
        triggerBoost(player);
    }

    /**
     * Normalizes the impulse vector, aligns it with player's look direction,
     * scales motion by boostSpeed, and applies verticalBoost while mitigating backward knockback.
     */
    private Vec3 calculateBoostVector(LocalPlayer player, Vec3 impulse) {
        Vec3 look = player.getLookAngle();
        double speed = boostSpeed.get();
        double vBoost = verticalBoost.get();

        // 3D look direction vector
        double lookLen = look.length();
        Vec3 lookNorm = lookLen > 1e-4 ? look.scale(1.0 / lookLen) : new Vec3(0, 0, 1);

        // Scale horizontal components along look vector
        double targetX = lookNorm.x * speed;
        double targetZ = lookNorm.z * speed;

        // Vertical component: allow upward look steering + configurable vertical lift
        double pitchUp = Math.max(0.0, lookNorm.y);
        double targetY = (pitchUp * speed * 0.5) + vBoost;

        // Ensure impulse is not inverted backwards (mitigate unexpected backward knockback)
        return new Vec3(targetX, targetY, targetZ);
    }

    private void triggerBoost(LocalPlayer player) {
        glideTicksRemaining = 40; // ~2 seconds of smooth glide traversal
        if (hasElytraEquipped(player) && !player.onGround() && !player.isFallFlying()) {
            player.startFallFlying();
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled() || mc.player == null) return;

        if (glideTicksRemaining > 0) {
            glideTicksRemaining--;
            if (mc.player.isFallFlying()) {
                Vec3 current = event.getMovement();
                Vec3 look = mc.player.getLookAngle();
                double targetSpeed = Math.max(boostSpeed.get() * 0.85, current.horizontalDistance());
                double newX = look.x * targetSpeed;
                double newZ = look.z * targetSpeed;
                double newY = Math.max(current.y, -0.05 + verticalBoost.get() * 0.08);
                event.setMovement(new Vec3(newX, newY, newZ));
            }
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null) return;

        if (mc.player.onGround()) {
            glideTicksRemaining = 0;
        }
    }

    private static boolean hasElytraEquipped(LocalPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return !chest.isEmpty() && chest.is(Items.ELYTRA);
    }
}
