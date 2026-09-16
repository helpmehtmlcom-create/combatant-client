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

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.events.impl.BlinkPacketEvent;
import combatant.client.events.impl.EventTargetChanged;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.util.player.NetworkStatsUtil;
import combatant.client.util.target.TargetManager;
import combatant.client.util.target.TargetingUtil;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ThreadLocalRandom;
/**
 * Headless incoming-packet backtrack controller adapted from LiquidBounce's Backtrack module.
 */
public final class BacktrackController {
    public static final BacktrackController INSTANCE = new BacktrackController();
    private final VecDeltaCodec delayedPosition = new VecDeltaCodec();
    private volatile boolean enabled;
    private Config config = Config.defaults();
    private Entity target;
    private final Deque<HitboxSample> hitboxHistory = new ConcurrentLinkedDeque<>();
    private int currentDelayMs = randomBetween(config.minDelayMs(), config.maxDelayMs());
    private int currentChance = ThreadLocalRandom.current().nextInt(0, 100);
    private long nextAllowedBacktrackMs;
    private long trackingBufferUntilMs;
    private long lastAttackMs;
    private boolean shouldPause;

    public record HitboxSample(long timestamp, Vec3 position, AABB boundingBox) {}

    private static boolean shouldAlwaysPass(Packet<?> packet) {
        if (packet == null) return false;
        return packet instanceof ServerboundChatPacket
                || packet instanceof ServerboundChatCommandPacket
                || packet instanceof ClientboundPingPacket
                || packet instanceof ClientboundKeepAlivePacket
                || packet instanceof ClientboundSystemChatPacket
                || isPlayerHurtSound(packet);
    }

    private static boolean isPlayerHurtSound(Packet<?> packet) {
        if (packet == null) return false;
        try {
            return packet instanceof ClientboundSoundPacket sound && sound.getSound() != null && sound.getSound().value() == SoundEvents.PLAYER_HURT
                    || packet instanceof ClientboundSoundEntityPacket entitySound
                    && entitySound.getSound() != null
                    && entitySound.getSound().value() == SoundEvents.PLAYER_HURT;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean shouldClearOnSafetyPacket(Packet<?> packet) {
        if (packet == null) return false;
        try {
            return packet instanceof ClientboundPlayerPositionPacket
                    || packet instanceof ClientboundDisconnectPacket
                    || packet instanceof ClientboundSetHealthPacket health && health.getHealth() <= 0.0f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isTargetEntityPacket(ClientboundMoveEntityPacket packet, Entity target, Minecraft mc) {
        if (packet == null || target == null || mc == null || mc.level == null) {
            return false;
        }
        Entity packetEntity = packet.getEntity(mc.level);
        return packetEntity != null && packetEntity.getId() == target.getId();
    }

    private static LivingEntity findEnemy(Minecraft mc, double minRange, double maxRange) {
        if (mc == null || mc.level == null || mc.player == null) {
            return null;
        }

        double safeMin = Math.max(0.0, Math.min(minRange, maxRange));
        double safeMax = Math.max(safeMin, Math.max(minRange, maxRange));
        AABB searchBox = mc.player.getBoundingBox().inflate(safeMax);
        double minRangeSq = safeMin * safeMin;
        double maxRangeSq = safeMax * safeMax;

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(mc.player, searchBox, e -> e instanceof LivingEntity)) {
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

    private static double boxedDistanceSq(Entity entity, Vec3 position) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || entity == null || position == null) {
            return Double.MAX_VALUE;
        }

        AABB movedBox = entity.getBoundingBox().move(position.subtract(entity.position()));
        return TargetingUtil.distanceToBoxSq(mc.player.getEyePosition(), movedBox);
    }

    private static int randomBetween(int a, int b) {
        int min = Math.max(0, Math.min(a, b));
        int max = Math.max(min, Math.max(a, b));
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        clear(!enabled);
    }

    public Config getConfig() {
        return config;
    }

    public void configure(Config config) {
        this.config = config != null ? config : Config.defaults();
        this.currentDelayMs = randomBetween(this.config.minDelayMs(), this.config.maxDelayMs());
    }

    public boolean isLagging() {
        return enabled && hasQueuedIncoming();
    }

    public Vec3 getTrackedPosition() {
        return delayedPosition.getBase();
    }

    public Entity getTarget() {
        return target;
    }

    public Deque<HitboxSample> getHitboxHistory() {
        return hitboxHistory;
    }

    public Vec3 getInterpolatedPosition(long targetTimeMs) {
        if (hitboxHistory.isEmpty()) {
            return target != null ? target.position() : delayedPosition.getBase();
        }
        HitboxSample before = null;
        HitboxSample after = null;
        for (HitboxSample sample : hitboxHistory) {
            if (sample.timestamp() <= targetTimeMs) {
                if (before == null || sample.timestamp() > before.timestamp()) {
                    before = sample;
                }
            }
            if (sample.timestamp() >= targetTimeMs) {
                if (after == null || sample.timestamp() < after.timestamp()) {
                    after = sample;
                }
            }
        }
        if (before == null && after == null) return target != null ? target.position() : delayedPosition.getBase();
        if (before == null) return after.position();
        if (after == null) return before.position();
        if (before.timestamp() == after.timestamp()) return before.position();

        double factor = (double) (targetTimeMs - before.timestamp()) / (after.timestamp() - before.timestamp());
        factor = Math.max(0.0, Math.min(1.0, factor));
        return before.position().lerp(after.position(), factor);
    }

    public AABB getInterpolatedHitbox(long targetTimeMs) {
        Vec3 pos = getInterpolatedPosition(targetTimeMs);
        if (target != null) {
            return target.getDimensions(target.getPose()).makeBoundingBox(pos);
        }
        return new AABB(pos, pos);
    }

    public AABB getPingInterpolatedHitbox() {
        Minecraft mc = Minecraft.getInstance();
        int ping = NetworkStatsUtil.getPing(mc);
        int delay = ping > 0 ? ping : currentDelayMs;
        return getInterpolatedHitbox(System.currentTimeMillis() - delay);
    }

    public void setTarget(Entity target) {
        processTarget(target);
    }
    public boolean shouldRenderTrackedBody() {
        Entity currentTarget = target;
        if (!enabled
                || !(currentTarget instanceof LivingEntity living)
                || !living.isAlive()
                || delayedPosition.getBase().equals(Vec3.ZERO)) {
            return false;
        }

        return config.targetMode() == TargetMode.RANGE
                || hasQueuedIncoming()
                || System.currentTimeMillis() - lastAttackMs <= config.lastAttackTimeToWorkMs();
    }

    @EventHandler
    public void onAttack(AttackEntityEvent event) {
        if (!enabled || event == null) {
            return;
        }

        lastAttackMs = System.currentTimeMillis();
        currentChance = ThreadLocalRandom.current().nextInt(0, 100);
        if (event.getTarget() instanceof LivingEntity living && TargetingUtil.isValidCombatTarget(living)) {
            processTarget(living);
        }
    }

    @EventHandler
    public void onTargetChanged(EventTargetChanged event) {
        if (!enabled) {
            return;
        }
        if (event.current != null && TargetingUtil.isValidCombatTarget(event.current)) {
            lastAttackMs = System.currentTimeMillis();
            processTarget(event.current);
        } else if (event.current == null && (target != null || hasQueuedIncoming())) {
            clear(true);
        }
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || mc.player.isDeadOrDying()) {
            clear(true);
            return;
        }

        // Integrate with TargetManager: only backtrack active enemy combat targets
        LivingEntity enemy = TargetManager.getTarget();
        if (enemy == null || !TargetingUtil.isValidCombatTarget(enemy) || enemy.isDeadOrDying()) {
            enemy = TargetManager.resolveTarget(mc.player, mc.level, config.maxRange(), TargetingUtil.TargetPriority.DISTANCE);
        }

        if (enemy != null && TargetingUtil.isValidCombatTarget(enemy) && !enemy.isDeadOrDying()) {
            processTarget(enemy);
        } else if (target != null || hasQueuedIncoming()) {
            clear(true);
        }

        // Accurately calculate target hitbox history and ping interpolation
        if (target instanceof LivingEntity living && living.isAlive()) {
            long now = System.currentTimeMillis();
            Vec3 pos = delayedPosition.getBase() != null && !delayedPosition.getBase().equals(Vec3.ZERO)
                    ? delayedPosition.getBase()
                    : target.position();
            AABB box = target.getDimensions(target.getPose()).makeBoundingBox(pos);
            hitboxHistory.addLast(new HitboxSample(now, pos, box));
            while (hitboxHistory.size() > 50 || (!hitboxHistory.isEmpty() && now - hitboxHistory.peekFirst().timestamp() > 1000L)) {
                hitboxHistory.pollFirst();
            }
        } else {
            hitboxHistory.clear();
        }

        boolean hadQueuedIncoming = hasQueuedIncoming();
        boolean shouldCancel = shouldCancelPackets();
        if (shouldCancel) {
            long now = System.currentTimeMillis();
            BlinkManager.INSTANCE.flush(snapshot ->
                    snapshot.origin() == TransferOrigin.INCOMING
                            && snapshot.timestamp() <= now - currentDelayMs
            );
        } else if (hadQueuedIncoming) {
            BlinkManager.INSTANCE.flush(TransferOrigin.INCOMING);
        }

        if (!hasQueuedIncoming()) {
            currentDelayMs = randomBetween(config.minDelayMs(), config.maxDelayMs());
        }
    }

    @EventHandler
    public void onBlinkPacket(BlinkPacketEvent event) {
        if (!enabled || event == null || event.getOrigin() != TransferOrigin.INCOMING) {
            return;
        }

        Packet<?> packet = event.getPacket();
        boolean shouldCancel = shouldCancelPackets();
        boolean hasQueuedIncoming = hasQueuedIncoming();

        if (packet == null) {
            if (shouldCancel || hasQueuedIncoming) {
                event.setAction(BlinkManager.Action.PASS);
            }
            return;
        }

        if (!hasQueuedIncoming && !shouldCancel) {
            return;
        }

        if (shouldAlwaysPass(packet)) {
            event.setAction(BlinkManager.Action.PASS);
            return;
        }

        if (shouldClearOnSafetyPacket(packet)) {
            clear(true);
            event.setAction(BlinkManager.Action.PASS);
            return;
        }

        Entity currentTarget = target;
        if (currentTarget == null) {
            return;
        }

        Vec3 nextTargetPos = getPacketTargetPosition(packet, currentTarget);
        if (nextTargetPos == null) {
            event.setAction(BlinkManager.Action.PASS);
            return;
        }

        if (boxedDistanceSq(currentTarget, nextTargetPos) < boxedDistanceSq(currentTarget, currentTarget.position())) {
            event.setAction(BlinkManager.Action.FLUSH);
            return;
        }

        event.setAction(BlinkManager.Action.QUEUE);
    }

    public void clear(boolean handlePackets) {
        clear(handlePackets, false, true);
    }

    private void clear(boolean handlePackets, boolean clearOnly, boolean resetNextBacktrackDelay) {
        if (handlePackets && !clearOnly) {
            BlinkManager.INSTANCE.flush(TransferOrigin.INCOMING);
        } else if (clearOnly) {
            BlinkManager.INSTANCE.getPacketQueue().removeIf(snapshot -> snapshot.origin() == TransferOrigin.INCOMING);
        }

        if (target != null && resetNextBacktrackDelay) {
            nextAllowedBacktrackMs = System.currentTimeMillis()
                    + randomBetween(config.minNextBacktrackDelayMs(), config.maxNextBacktrackDelayMs());
        }

        target = null;
        delayedPosition.setBase(Vec3.ZERO);
        hitboxHistory.clear();
        shouldPause = false;
    }
    private void processTarget(Entity enemy) {
        if (!(enemy instanceof LivingEntity living)) {
            return;
        }

        shouldPause = living.hurtTime >= config.pauseHurtTime();
        if (!shouldBacktrack(living)) {
            return;
        }

        if (enemy != target) {
            clear(true, false, false);
            delayedPosition.setBase(enemy.getPositionCodec().getBase());
        }

        target = enemy;
    }

    private boolean shouldCancelPackets() {
        return target instanceof LivingEntity living && living.isAlive() && shouldBacktrack(living);
    }

    private boolean shouldBacktrack(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || !TargetingUtil.isValidCombatTarget(entity)) {
            return false;
        }

        long now = System.currentTimeMillis();
        boolean inRange = boxedDistanceSq(entity, entity.position()) >= config.minRange() * config.minRange()
                && boxedDistanceSq(entity, entity.position()) <= config.maxRange() * config.maxRange();
        if (inRange) {
            trackingBufferUntilMs = now + config.trackingBufferMs();
        }

        boolean attackWindow = config.targetMode() != TargetMode.ATTACK
                || now - lastAttackMs <= config.lastAttackTimeToWorkMs();

        return (inRange || now < trackingBufferUntilMs)
                && mc.player.tickCount > 10
                && currentChance < config.chancePercent()
                && now >= nextAllowedBacktrackMs
                && !shouldPause()
                && attackWindow;
    }

    private boolean shouldPause() {
        return config.pauseOnHurtTime() && shouldPause;
    }

    private boolean hasQueuedIncoming() {
        return BlinkManager.INSTANCE.getPacketQueue().stream().anyMatch(snapshot -> snapshot.origin() == TransferOrigin.INCOMING);
    }

    private Vec3 getPacketTargetPosition(Packet<?> packet, Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc == null || mc.level == null) {
            return null;
        }

        if (packet instanceof ClientboundMoveEntityPacket entityPacket && isTargetEntityPacket(entityPacket, target, mc)) {
            Vec3 pos = delayedPosition.decode(
                    entityPacket.getXa(),
                    entityPacket.getYa(),
                    entityPacket.getZa()
            );
            if (pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)) {
                delayedPosition.setBase(pos);
                return pos;
            }
            return null;
        }
        if (packet instanceof ClientboundTeleportEntityPacket positionPacket && positionPacket.id() == target.getId()) {
            Vec3 currentBase = delayedPosition.getBase();
            if (currentBase == null || !Double.isFinite(currentBase.x) || !Double.isFinite(currentBase.y) || !Double.isFinite(currentBase.z)) {
                currentBase = target.position();
            }
            PositionMoveRotation current = new PositionMoveRotation(
                    currentBase,
                    target.getDeltaMovement(),
                    target.getYRot(),
                    target.getXRot()
            );
            Vec3 pos = PositionMoveRotation.calculateAbsolute(current, positionPacket.change(), positionPacket.relatives()).position();
            if (pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)) {
                delayedPosition.setBase(pos);
                return pos;
            }
            return null;
        }
        if (packet instanceof ClientboundEntityPositionSyncPacket syncPacket && syncPacket.id() == target.getId()) {
            if (syncPacket.values() != null) {
                Vec3 pos = syncPacket.values().position();
                if (pos != null && Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z)) {
                    delayedPosition.setBase(pos);
                    return pos;
                }
            }
            return null;
        }
        return null;
    }

    public enum TargetMode implements EnumValue.IdProvider {
        ATTACK("attack"),
        RANGE("range");

        private final String id;

        TargetMode(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }
    }

    public record Config(
            double maxRange,
            int minDelayMs,
            int maxDelayMs
    ) {
        public Config {
            maxRange = Math.max(1.0, maxRange);
            minDelayMs = Math.max(0, minDelayMs);
            maxDelayMs = Math.max(minDelayMs, maxDelayMs);
        }

        public static Config defaults() {
            return new Config(3.5, 100, 150);
        }

        public Config(
                double minRange,
                double maxRange,
                int minDelayMs,
                int maxDelayMs,
                int minNextBacktrackDelayMs,
                int maxNextBacktrackDelayMs,
                int trackingBufferMs,
                int chancePercent,
                boolean pauseOnHurtTime,
                int pauseHurtTime,
                int lastAttackTimeToWorkMs,
                TargetMode targetMode
        ) {
            this(maxRange, minDelayMs, maxDelayMs);
        }

        public double minRange() { return 0.0; }
        public int minNextBacktrackDelayMs() { return 0; }
        public int maxNextBacktrackDelayMs() { return 10; }
        public int trackingBufferMs() { return 500; }
        public int chancePercent() { return 100; }
        public boolean pauseOnHurtTime() { return false; }
        public int pauseHurtTime() { return 3; }
        public int lastAttackTimeToWorkMs() { return 1000; }
        public TargetMode targetMode() { return TargetMode.ATTACK; }
    }
}
