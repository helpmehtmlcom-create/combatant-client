/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.effects.CurrentTransientEffectBackend;
import combatant.client.render.effects.EffectBudget;
import combatant.client.render.effects.kernels.TransientAttackKernels;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * World-space totem pop VFX.
 *
 * <p>There is deliberately no fullscreen/color-wash pass here. Both local and attributed enemy
 * pops are spatial bursts with scene depth. Enemy pops only fire when that exact player was hit by
 * the local player shortly before the server announces the totem event.</p>
 */
@ModuleInfo(id = "totemfx", displayName = "TotemFX", category = ModuleCategory.VISUALS,
        subcategory = ModuleSubcategory.COSMETIC, description = "module.totemfx.description")
public class TotemFX extends Module {
    private static final String SETTING_SELF_POP = "self_pop";
    private static final String SETTING_ATTACKED_POP = "attacked_pop";
    private static final String SETTING_BURST_SCALE = "burst_scale";
    private static final String SETTING_DURATION = "duration";
    private static final String SETTING_DENSITY = "density";

    /** Long enough for normal packet latency, short enough to avoid unrelated nearby pops. */
    private static final long ATTACK_ATTRIBUTION_WINDOW_MS = 1_250L;
    private static final long ATTACK_HISTORY_RETENTION_MS = 2_500L;

    private static final int TOTEM_GOLD = 0xFFFFD36A;
    private static final int TOTEM_MINT = 0xFF68FFD5;
    private static final int IMPACT_CORAL = 0xFFFF667A;
    private static final int IMPACT_HOT = 0xFFFFA24F;

    private final Minecraft mc = Minecraft.getInstance();
    private final BooleanValue selfPop = bool("totemFxSelfPop", SETTING_SELF_POP, true);
    private final BooleanValue attackedPop = bool("totemFxAttackedPop", SETTING_ATTACKED_POP, true);
    private final NumberValue<Float> burstScale = num("totemFxBurstScale", SETTING_BURST_SCALE, 1.0f, 0.45f, 2.5f);
    private final NumberValue<Integer> durationMs = num("totemFxDuration", SETTING_DURATION, 700, 320, 1600);
    private final NumberValue<Float> density = num("totemFxDensity", SETTING_DENSITY, 1.0f, 0.45f, 2.0f);

    private final CurrentTransientEffectBackend worldEffects =
            new CurrentTransientEffectBackend(new EffectBudget(128, 48, Integer.MIN_VALUE));
    private final Map<UUID, Long> recentLocalAttacks = new HashMap<>();
    private long nextEffectId = 1L;

    {
        TransientAttackKernels.register(worldEffects);
    }

    @EventHandler
    private void onAttackEntity(AttackEntityEvent event) {
        if (!isEnabled() || event == null || event.getPlayer() != mc.player) return;
        if (!(event.getTarget() instanceof Player player) || player == mc.player) return;

        long now = System.currentTimeMillis();
        recentLocalAttacks.put(player.getUUID(), now);
        pruneAttackHistory(now);
    }

    /** Compatibility entry used by older callers: a local-player totem pop. */
    public void onTotemPop() {
        onLocalTotemPop();
    }

    public void onLocalTotemPop() {
        if (!isEnabled() || !selfPop.get() || mc.player == null || mc.level == null) return;
        spawnBurst(mc.player, BurstKind.SELF, System.currentTimeMillis());
    }

    /**
     * Called for another player's status-35 event. The effect is emitted only when the local player
     * attacked this exact player within the attribution window.
     */
    public void onOpponentTotemPop(Player player) {
        if (!isEnabled() || !attackedPop.get() || player == null || mc.player == null || mc.level == null) return;
        if (player == mc.player) return;

        long now = System.currentTimeMillis();
        Long attackedAt = recentLocalAttacks.get(player.getUUID());
        if (attackedAt == null || now - attackedAt < 0L || now - attackedAt > ATTACK_ATTRIBUTION_WINDOW_MS) {
            pruneAttackHistory(now);
            return;
        }

        // Consume the attribution so a duplicated/stale status packet cannot emit another burst.
        recentLocalAttacks.remove(player.getUUID());
        spawnBurst(player, BurstKind.ATTACKED, now);
        pruneAttackHistory(now);
    }

    private void spawnBurst(Player player, BurstKind kind, long now) {
        float scale = burstScale.get();
        float densityMul = density.get();
        long life = Math.max(320L, durationMs.get());
        Vec3 center = player.position().add(0.0, player.getBbHeight() * (kind == BurstKind.SELF ? 0.52 : 0.55), 0.0);
        int sourceId = player.getId();

        worldEffects.beginTick(mc.player != null ? mc.player.tickCount : now);
        if (kind == BurstKind.SELF) {
            // Defensive pop: compact gold/mint core, then a clean volumetric shell and sparks.
            worldEffects.spawn(TransientAttackKernels.radialBurst(
                    nextEffectId++, center, now, life,
                    mixSeed(now, sourceId, 0x51F15EEDL), sourceId,
                    TOTEM_GOLD, TOTEM_MINT,
                    2.15f * scale, Math.round(46.0f * densityMul), 1.15f, 0.30f, 120
            ));
            worldEffects.spawn(TransientAttackKernels.plasma(
                    nextEffectId++, center, now, Math.round(life * 0.78f),
                    mixSeed(now, sourceId, 0x6A09E667L), sourceId,
                    TOTEM_GOLD, TOTEM_MINT,
                    1.55f * scale, 1.12f, 110
            ));
            worldEffects.spawn(TransientAttackKernels.sparks(
                    nextEffectId++, center, now, Math.round(life * 1.03f),
                    mixSeed(now, sourceId, 0xBB67AE85L), sourceId,
                    TOTEM_MINT, TOTEM_GOLD,
                    3.05f * scale, Math.round(30.0f * densityMul), 1.08f, 105
            ));
            return;
        }

        // Offensive confirmation: larger/hotter shell at the popped target with a harder radial kick.
        worldEffects.spawn(TransientAttackKernels.radialBurst(
                nextEffectId++, center, now, Math.round(life * 1.06f),
                mixSeed(now, sourceId, 0xA77ACED1L), sourceId,
                IMPACT_CORAL, TOTEM_GOLD,
                2.65f * scale, Math.round(58.0f * densityMul), 1.38f, 0.34f, 130
        ));
        worldEffects.spawn(TransientAttackKernels.plasma(
                nextEffectId++, center, now, Math.round(life * 0.82f),
                mixSeed(now, sourceId, 0x3C6EF372L), sourceId,
                IMPACT_HOT, IMPACT_CORAL,
                1.95f * scale, 1.34f, 120
        ));
        worldEffects.spawn(TransientAttackKernels.sparks(
                nextEffectId++, center, now, Math.round(life * 1.10f),
                mixSeed(now, sourceId, 0x9E3779B9L), sourceId,
                TOTEM_GOLD, IMPACT_CORAL,
                3.75f * scale, Math.round(40.0f * densityMul), 1.32f, 115
        ));
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        worldEffects.render(renderer, tickDelta, now);
        pruneAttackHistory(now);
    }

    @Override
    public void onDisable() {
        worldEffects.clear();
        recentLocalAttacks.clear();
    }

    private void pruneAttackHistory(long now) {
        Iterator<Map.Entry<UUID, Long>> iterator = recentLocalAttacks.entrySet().iterator();
        while (iterator.hasNext()) {
            long age = now - iterator.next().getValue();
            if (age < 0L || age > ATTACK_HISTORY_RETENTION_MS) iterator.remove();
        }
    }

    private static long mixSeed(long now, int sourceId, long salt) {
        long z = now ^ (((long) sourceId) << 32) ^ salt;
        z ^= z >>> 30;
        z *= 0xBF58476D1CE4E5B9L;
        z ^= z >>> 27;
        z *= 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private enum BurstKind {
        SELF,
        ATTACKED
    }
}
