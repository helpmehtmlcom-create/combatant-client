/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.pvp.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import combatant.client.config.values.ItemCooldownRulesValue;
import combatant.client.config.MainConfig;
import combatant.client.util.pvp.ItemCooldownSnapshot;
import combatant.client.util.pvp.ItemUseCooldowns;

import java.util.Map;

/**
 * Local-player PvP state facade.
 * <p>
 * Item cooldown storage is not kept here. It delegates to the generic owner-based
 * ItemUseCooldowns engine with SELF_OWNER, so self/opponent/future systems all use
 * the same rule/window/cooldown implementation.
 */
public class CooldownManager {
    private boolean inPvp = false;
    private long lastPvpExitMs = 0L;

    public void enterPvp() {
        inPvp = true;
        lastPvpExitMs = 0L;
    }

    public void exitPvp() {
        inPvp = false;
        lastPvpExitMs = Util.getMillis();
    }

    public boolean isInPvp() {
        return MainConfig.get().isForcePvp() || inPvp;
    }

    public boolean isPvpGraceActive(long graceMs) {
        if (isInPvp()) return true;
        if (lastPvpExitMs <= 0L) return false;
        return Util.getMillis() - lastPvpExitMs <= graceMs;
    }

    /** No-op compatibility hook. */
    public void beginPredictedUse(Item item) {
        // no-op
    }

    /** Records a confirmed self-use against configured item cooldown rules. */
    public void commitConfirmedUse(Item item, long startedAtMs) {
        ItemUseCooldowns.recordSelfUse(item);
    }

    public ItemUseCooldowns.RuleUseResult recordRuleUse(Item item, ItemCooldownRulesValue.Rule rule) {
        return ItemUseCooldowns.recordUse(ItemUseCooldowns.SELF_OWNER, item, rule);
    }

    public void startLocalCooldown(Item item, int seconds) {
        ItemUseCooldowns.startSelfCooldown(item, seconds);
    }

    public float getCooldownProgress(Item item) {
        return snapshot(item).cooldownProgress();
    }

    public boolean isCooling(Item item) {
        return ItemUseCooldowns.isSelfCooling(item);
    }

    public ItemCooldownSnapshot snapshot(Item item) {
        return ItemUseCooldowns.selfSnapshot(item);
    }

    public Map<Item, ItemCooldownSnapshot> snapshots() {
        return ItemUseCooldowns.selfSnapshots();
    }

    public void clear() {
        ItemUseCooldowns.clearSelf();
        lastPvpExitMs = 0L;
    }

    public boolean isPredicted(Item item) {
        return false;
    }

    /**
     * Returns the attack strength scale (0.0 to 1.0) of the local player for weapon swings.
     *
     * @param adjustTicks tick adjustment offset
     * @return current attack cooldown progress, or {@code 1.0f} if player is null
     */
    public float getAttackStrengthScale(float adjustTicks) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getAttackStrengthScale(adjustTicks) : 1.0f;
    }

    /**
     * Returns whether the player's weapon swing cooldown is fully recharged.
     *
     * @return {@code true} if attack strength scale is >= 1.0
     */
    public boolean isAttackReady() {
        return getAttackStrengthScale(0.0f) >= 1.0f;
    }

    /**
     * Returns whether the given item is currently on vanilla item cooldown for the local player.
     *
     * @param item the item to query
     * @return {@code true} if on cooldown, {@code false} if not or if player/item is null
     */
    public boolean isItemOnVanillaCooldown(Item item) {
        if (item == null) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getCooldowns().isOnCooldown(item.getDefaultInstance());
    }

    /**
     * Returns the vanilla item cooldown progress percentage (0.0 to 1.0) for the given item.
     *
     * @param item         the item to query
     * @param partialTicks render partial ticks
     * @return cooldown percentage remaining, or {@code 0.0f} if not on cooldown or player/item is null
     */
    public float getVanillaCooldownPercent(Item item, float partialTicks) {
        if (item == null) {
            return 0.0f;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null ? player.getCooldowns().getCooldownPercent(item.getDefaultInstance(), partialTicks) : 0.0f;
    }
}
