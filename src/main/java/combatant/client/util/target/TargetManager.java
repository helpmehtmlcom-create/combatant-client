/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.target;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.events.Events;
import combatant.client.events.impl.EventTargetChanged;

import java.util.EnumMap;
import java.util.List;

public enum TargetManager {
    ;

    private static final long DEFAULT_ATTACK_HOLD_MS = 700L;
    private static final EnumMap<Source, TargetState> STATES = new EnumMap<>(Source.class);
    private static LivingEntity current;
    private static Source currentSource;
    private static long lastCombatTimeMs;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null) {
            clearAll();
            return;
        }

        if (current != null || mc.player.hurtTime > 0) {
            markCombat();
        }

        updateCrosshair(mc);
        purgeInvalids();
        refreshCurrent();
    }

    public static boolean isInCombat() {
        return current != null || (System.currentTimeMillis() - lastCombatTimeMs <= 5000L);
    }

    public static void markCombat() {
        lastCombatTimeMs = System.currentTimeMillis();
    }

    public static long getLastCombatTimeMs() {
        return lastCombatTimeMs;
    }

    public static boolean isValidPlayerTarget(Player self, Player target, double range) {
        return TargetingUtil.isValidPlayerTarget(self, target, range);
    }

    public static boolean isBurrowed(Player player, Level level) {
        return TargetingUtil.isBurrowed(player, level);
    }

    public static boolean isSurrounded(Player player, Level level) {
        return TargetingUtil.isSurrounded(player, level);
    }

    public static TargetingUtil.HoleStatus getHoleStatus(Player player, Level level) {
        return TargetingUtil.getHoleStatus(player, level);
    }

    public static Player findBestTarget(Player self, Level level, double range, TargetingUtil.TargetPriority priority) {
        return TargetingUtil.findBestTarget(self, level, range, priority);
    }

    public static List<Player> findTargets(Player self, Level level, double range, TargetingUtil.TargetPriority priority) {
        return TargetingUtil.findPlayers(self, level, range, priority);
    }

    /**
     * Resolves the primary combat target using the unified TargetManager state,
     * falling back to the best candidate in range if no active target is tracked.
     */
    public static LivingEntity resolveTarget(Player self, Level level, double range, TargetingUtil.TargetPriority priority) {
        LivingEntity active = current;
        if (active != null && TargetingUtil.isValidCombatTarget(active)) {
            if (active instanceof Player p) {
                if (TargetingUtil.isValidPlayerTarget(self, p, range)) {
                    return active;
                }
            } else if (self == null || self.distanceTo(active) <= range) {
                return active;
            }
        }
        return findBestTarget(self, level, range, priority);
    }

    public static LivingEntity getTarget() {
        return current;
    }

    public static LivingEntity getTarget(boolean includeCrosshair) {
        if (includeCrosshair) {
            return current;
        }
        TargetSelection selection = selectCurrent(false);
        return selection.entity();
    }

    public static Source getTargetSource() {
        return currentSource;
    }

    public static void onAttack(Entity target) {
        markCombat();
        if (target instanceof LivingEntity living && TargetingUtil.isValidCombatTarget(living)) {
            push(Source.ATTACK, living, DEFAULT_ATTACK_HOLD_MS);
        }
    }

    public static void setForcedTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.FORCE);
            refreshCurrent();
            return;
        }
        push(Source.FORCE, target, 0L);
        refreshCurrent();
    }

    public static void setModuleTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.MODULE);
            refreshCurrent();
            return;
        }
        push(Source.MODULE, target, 0L);
        refreshCurrent();
    }

    public static void setAutoCrystalTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.AUTO_CRYSTAL);
            refreshCurrent();
            return;
        }
        push(Source.AUTO_CRYSTAL, target, 0L);
        refreshCurrent();
    }

    public static void setAutoAnchorTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.AUTO_ANCHOR);
            refreshCurrent();
            return;
        }
        push(Source.AUTO_ANCHOR, target, 0L);
        refreshCurrent();
    }

    public static void setAutoBedTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.AUTO_BED);
            refreshCurrent();
            return;
        }
        push(Source.AUTO_BED, target, 0L);
        refreshCurrent();
    }

    public static void setPredictionTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.PREDICTION);
            refreshCurrent();
            return;
        }
        push(Source.PREDICTION, target, 0L);
        refreshCurrent();
    }

    public static void setElytraTarget(LivingEntity target) {
        if (target == null) {
            clear(Source.ELYTRA);
            refreshCurrent();
            return;
        }
        push(Source.ELYTRA, target, 0L);
        refreshCurrent();
    }

    public static void clear(Source source) {
        if (source == null) return;
        STATES.remove(source);
    }

    public static void clearAll() {
        STATES.clear();
        if (current != null) {
            LivingEntity prev = current;
            Source prevSource = currentSource;
            current = null;
            currentSource = null;
            Events.BUS.post(new EventTargetChanged(prev, prevSource, null, null));
        } else {
            current = null;
            currentSource = null;
        }
    }

    private static void push(Source source, LivingEntity target, long holdMs) {
        if (source == null || target == null) return;
        STATES.put(source, new TargetState(target, System.currentTimeMillis(), holdMs));
    }

    private static void updateCrosshair(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (hr instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity living
                && TargetingUtil.isValidCombatTarget(living)) {
            push(Source.CROSSHAIR, living, 0L);
        } else {
            clear(Source.CROSSHAIR);
        }
    }

    private static void purgeInvalids() {
        long now = System.currentTimeMillis();
        STATES.entrySet().removeIf(entry -> {
            TargetState state = entry.getValue();
            if (state == null || state.entity == null) return true;
            if (!TargetingUtil.isValidCombatTarget(state.entity)) return true;
            if (state.holdMs <= 0L) return false;
            return (now - state.lastSeenMs) > state.holdMs;
        });
    }

    private static void refreshCurrent() {
        LivingEntity prev = current;
        Source prevSource = currentSource;

        TargetSelection next = selectCurrent(true);
        LivingEntity nextEntity = next.entity();
        Source nextSource = next.source();

        if (prev != nextEntity || prevSource != nextSource) {
            current = nextEntity;
            currentSource = nextSource;
            Events.BUS.post(new EventTargetChanged(prev, prevSource, nextEntity, nextSource));
        }
    }

    private static TargetSelection selectCurrent(boolean includeCrosshair) {
        for (Source source : new Source[]{Source.FORCE, Source.ELYTRA, Source.AUTO_CRYSTAL, Source.AUTO_ANCHOR, Source.AUTO_BED, Source.MODULE, Source.PREDICTION, Source.ATTACK, Source.CROSSHAIR}) {
            if (!includeCrosshair && source == Source.CROSSHAIR) continue;
            TargetState state = STATES.get(source);
            if (state != null && TargetingUtil.isValidCombatTarget(state.entity)) {
                return new TargetSelection(state.entity, source);
            }
        }
        return TargetSelection.EMPTY;
    }

    public enum Source {
        FORCE,
        ELYTRA,
        AUTO_CRYSTAL,
        AUTO_ANCHOR,
        AUTO_BED,
        MODULE,
        PREDICTION,
        ATTACK,
        CROSSHAIR
    }

    private record TargetState(LivingEntity entity, long lastSeenMs, long holdMs) {
    }

    private record TargetSelection(LivingEntity entity, Source source) {
        private static final TargetSelection EMPTY = new TargetSelection(null, null);
    }
}
