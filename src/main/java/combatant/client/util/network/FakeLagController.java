/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.network;

import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.util.item.FoodUtil;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Headless fake-lag policy controller adapted from LiquidBounce's FakeLag module.
 */
public final class FakeLagController {
    public static final FakeLagController INSTANCE = new FakeLagController();

    private volatile boolean enabled;
    private Config config = Config.defaults();
    private long nextDelayMs = calculateDelay(config);
    private long recoilUntilMs;
    private boolean enemyNearby;
    private int lastHurtTime;
    private FakeLagController() {
    }

    private static boolean shouldPassOnSafetyPacket(Packet<?> packet, LocalPlayer player) {
        if (packet == null || player == null) {
            return false;
        }
        try {
            if (packet instanceof ClientboundPlayerPositionPacket || packet instanceof ServerboundResourcePackPacket) {
                return true;
            }
            if (packet instanceof ClientboundSetEntityMotionPacket(int id, Vec3 movement)) {
                return id == player.getId() && movement != null && movement != Vec3.ZERO;
            }
            if (packet instanceof ClientboundExplodePacket explosion) {
                return explosion.playerKnockback() != null
                        && explosion.playerKnockback().isPresent()
                        && explosion.playerKnockback().get() != null
                        && explosion.playerKnockback().get() != Vec3.ZERO;
            }
            return packet instanceof ClientboundSetHealthPacket;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isConsumable(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (FoodUtil.isFood(stack) || stack.get(DataComponents.CONSUMABLE) != null);
    }

    private static LivingEntity findEnemy(Minecraft mc, double minRange, double maxRange) {
        if (mc == null || mc.level == null || mc.player == null) {
            return null;
        }
        double safeMin = Math.max(0.0, Math.min(minRange, maxRange));
        double safeMax = Math.max(safeMin, Math.max(minRange, maxRange));
        AABB box = mc.player.getBoundingBox().inflate(safeMax);
        double minRangeSq = safeMin * safeMin;
        double maxRangeSq = safeMax * safeMax;
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof LivingEntity)) {
            LivingEntity living = (LivingEntity) entity;
            if (!TargetingUtil.isValidCombatTarget(living)) {
                continue;
            }
            double dist = TargetingUtil.distanceToEntityBoxSq(mc.player.getEyePosition(), living);
            if (dist >= minRangeSq && dist <= maxRangeSq && dist < bestDist) {
                best = living;
                bestDist = dist;
            }
        }
        return best;
    }

    private static long calculateDelay(Config config) {
        if (config == null) return 200L;
        if (config.mode() == Mode.STATIC || config.mode() == Mode.CONSTANT) {
            return config.latencyMs();
        }
        int min = Math.max(10, (int) (config.latencyMs() * 0.5));
        int max = Math.max(min, config.latencyMs());
        return ThreadLocalRandom.current().nextLong(min, max + 1);
    }

    private static long randomDelay(Config config) {
        return calculateDelay(config);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            enemyNearby = false;
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        } else {
            nextDelayMs = calculateDelay(config);
            recoilUntilMs = 0L;
            lastHurtTime = 0;
        }
    }

    public Config getConfig() {
        return config;
    }

    public void configure(Config config) {
        this.config = config != null ? config : Config.defaults();
        this.nextDelayMs = calculateDelay(this.config);
    }

    @EventHandler
    public void onAttack(AttackEntityEvent event) {
        if (!enabled || BlinkManager.INSTANCE.isBlinking()) {
            return;
        }
        // Automatically flush packets when attacking to ensure hit registration
        BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
        recoilUntilMs = System.currentTimeMillis() + 150L;
    }

    @EventHandler
    public void onPacket(PacketEvent event) {
        if (!enabled || BlinkManager.INSTANCE.isBlinking() || event == null || event.getPacket() == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (player == null) return;

        Packet<?> packet = event.getPacket();
        if (event.getOrigin() == TransferOrigin.INCOMING) {
            boolean tookDamage = false;
            if (packet instanceof ClientboundHurtAnimationPacket hurt && hurt.id() == player.getId()) {
                tookDamage = true;
            } else if (packet instanceof ClientboundDamageEventPacket dmg && dmg.entityId() == player.getId()) {
                tookDamage = true;
            } else if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == player.getId()) {
                tookDamage = true;
            } else if (packet instanceof ClientboundExplodePacket explosion
                    && explosion.playerKnockback() != null
                    && explosion.playerKnockback().isPresent()) {
                tookDamage = true;
            }

            if (tookDamage) {
                // Automatically flush packets when taking damage to ensure hit/knockback registration
                BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
                recoilUntilMs = System.currentTimeMillis() + 200L;
            }
        }
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!enabled || BlinkManager.INSTANCE.isBlinking()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (mc == null || mc.level == null || player == null || player.isDeadOrDying()) {
            enemyNearby = false;
            return;
        }

        if (player.hurtTime > 0 && player.hurtTime != lastHurtTime) {
            lastHurtTime = player.hurtTime;
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
            recoilUntilMs = System.currentTimeMillis() + 200L;
            return;
        }
        lastHurtTime = player.hurtTime;

        LivingEntity target = TargetManager.getTarget();
        if (target != null && TargetingUtil.isValidCombatTarget(target)) {
            enemyNearby = true;
        } else {
            enemyNearby = findEnemy(mc, 1.0, 6.0) != null;
        }
    }

    @EventHandler
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!enabled || event.getOrigin() != TransferOrigin.OUTGOING) {
            return;
        }

        // Suspend interval flushes and delay changes while Blink is actively withholding packets
        if (BlinkManager.INSTANCE.isBlinking()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        if (mc == null || mc.level == null || player == null || player.isDeadOrDying() || player.isInWater() || ClientScreen.current() != null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < recoilUntilMs) {
            return;
        }

        if (BlinkManager.INSTANCE.isAboveTime(nextDelayMs)) {
            nextDelayMs = calculateDelay(config);
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet == null) {
            if (config.mode() == Mode.STATIC || config.mode() == Mode.CONSTANT || enemyNearby) {
                event.setAction(BlinkManager.Action.QUEUE);
            }
            return;
        }

        if (shouldPassOnSafetyPacket(packet, player)) {
            recoilUntilMs = now + 150L;
            return;
        }

        if (packet instanceof ServerboundInteractPacket || packet instanceof ServerboundSwingPacket) {
            // Attack packet safety: flush immediately for hit registration
            BlinkManager.INSTANCE.flush(TransferOrigin.OUTGOING);
            recoilUntilMs = now + 150L;
            return;
        }

        if (player.isUsingItem() && isConsumable(player.getUseItem())) {
            return;
        }

        if (config.mode() == Mode.STATIC || config.mode() == Mode.CONSTANT) {
            event.setAction(BlinkManager.Action.QUEUE);
            return;
        }

        if (!enemyNearby) {
            return;
        }

        Vec3 serverPosition = BlinkManager.INSTANCE.getQueuedMovePositions().stream().findFirst().orElse(player.position());
        AABB serverBox = player.getDimensions(player.getPose()).makeBoundingBox(serverPosition);
        LivingEntity enemy = TargetManager.getTarget();
        if (enemy == null || !TargetingUtil.isValidCombatTarget(enemy)) {
            enemy = findEnemy(mc, 1.0, 6.0);
        }
        if (enemy == null) {
            return;
        }

        double serverDistance = enemy.position().distanceTo(serverPosition);
        double clientDistance = enemy.position().distanceTo(player.position());
        if (serverDistance < clientDistance || enemy.getBoundingBox().intersects(serverBox)) {
            return;
        }

        event.setAction(BlinkManager.Action.QUEUE);
    }

    public enum Mode implements EnumValue.IdProvider {
        DYNAMIC("dynamic"),
        STATIC("static"),
        CONSTANT("constant");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public enum FlushOn {
        ENTITY_INTERACT,
        BLOCK_INTERACT,
        ACTION;

        boolean test(Packet<?> packet) {
            return switch (this) {
                case ENTITY_INTERACT -> packet instanceof ServerboundInteractPacket
                        || packet instanceof ServerboundSwingPacket;
                case BLOCK_INTERACT -> packet instanceof ServerboundUseItemOnPacket
                        || packet instanceof ServerboundSignUpdatePacket;
                case ACTION -> packet instanceof ServerboundPlayerActionPacket;
            };
        }
    }

    public record Config(
            int latencyMs,
            Mode mode
    ) {
        public Config {
            if (mode == null) mode = Mode.DYNAMIC;
            latencyMs = Math.max(0, latencyMs);
        }

        public static Config defaults() {
            return new Config(200, Mode.DYNAMIC);
        }

        public Config(double minRange, double maxRange, int minDelayMs, int maxDelayMs, int recoilTimeMs, Mode mode, EnumSet<FlushOn> flushOn) {
            this(maxDelayMs > 0 ? maxDelayMs : 200, mode != null ? mode : Mode.DYNAMIC);
        }

        public double minRange() { return 1.0; }
        public double maxRange() { return 6.0; }
        public int minDelayMs() { return (int) (latencyMs * 0.5); }
        public int maxDelayMs() { return latencyMs; }
        public int recoilTimeMs() { return 150; }
        public EnumSet<FlushOn> flushOn() { return EnumSet.noneOf(FlushOn.class); }
    }
}
