/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat.autocrystal;

import combatant.client.util.combat.ExplosionDamageCandidate;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class AutoCrystalSelectionUtil {
    private AutoCrystalSelectionUtil() {
    }

    public static <T extends ExplosionDamageCandidate> T selectBest(
            List<T> candidates,
            LivingEntity target,
            float minDamage,
            float faceplaceHealth
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        T bestData = null;
        float bestVal = 0.0f;

        for (T data : candidates) {
            if (data == null) {
                continue;
            }
            float damage = data.damage();
            float selfDamage = data.selfDamage();
            if (Float.isNaN(damage) || Float.isNaN(selfDamage) || Float.isInfinite(damage) || Float.isInfinite(selfDamage)) {
                continue;
            }

            if (!(AutoCrystalDamageRules.shouldOverrideMinDamage(target, damage, faceplaceHealth) || damage > minDamage)) {
                continue;
            }

            if (bestData != null
                    && data.overrideDamage()
                    && target != null
                    && target.getAbsorptionAmount() + target.getHealth() < bestData.damage()
                    && bestData.selfDamage() < selfDamage) {
                continue;
            }

            boolean shouldStopOverride = bestData != null
                    && bestData.overrideDamage()
                    && target != null
                    && damage > target.getHealth() + target.getAbsorptionAmount()
                    && selfDamage < bestData.selfDamage();

            float safetyComparatorDelta = shouldStopOverride ? 10.0f : 1.0f;

            if (bestData != null
                    && Math.abs(bestData.damage() - damage) < safetyComparatorDelta
                    && Math.abs(bestData.selfDamage() - selfDamage) > 1.0f) {
                if (bestData.selfDamage() >= selfDamage) {
                    bestData = data;
                    bestVal = damage;
                }
            } else if (bestData == null || bestVal < damage) {
                bestData = data;
                bestVal = damage;
            }
        }

        return bestData;
    }
}
