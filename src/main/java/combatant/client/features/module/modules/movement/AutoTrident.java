/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.EventSync;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

@ModuleInfo(
        id = "auto_trident",
        displayName = "Auto Trident",
        category = ModuleCategory.MOVEMENT,
        subCategory = ModuleSubCategory.FLIGHT,
        description = "Automatically uses Riptide tridents with silent rotation and height maintenance for infinite flight on Grim and DonutSMP.",
        aliases = {"tridentfly", "riptidefly", "autoriptide"}
)
public class AutoTrident extends Module {

    public enum Mode implements EnumValue.IdProvider {
        HEIGHT_MAINTENANCE("maintain_height", "Maintain Height"),
        FORWARD("forward", "Boost Forward"),
        UPWARD("upward", "Ascend");

        private final String id;
        private final String displayName;

        Mode(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private final EnumValue<Mode> mode =
            enumMode("mode", Mode.HEIGHT_MAINTENANCE, Mode.values());

    private final NumberValue<Double> targetHeight =
            visibleWhen(num("target_height", "target_height", 180.0, 60.0, 320.0), () -> mode.get() == Mode.HEIGHT_MAINTENANCE);

    private final BooleanValue silentRotation =
            bool("silent_rotation", "silent_rotation", true);

    private final BooleanValue grimMode =
            bool("grim_mode", "grim_mode", true);

    private final BooleanValue weatherCheck =
            bool("weather_check", "weather_check", true);

    private final BooleanValue autoSwap =
            bool("auto_swap", "auto_swap", true);

    private final NumberValue<Integer> minChargeTicks =
            num("min_charge_ticks", "min_charge_ticks", 10, 10, 25);

    private final NumberValue<Integer> cooldownTicks =
            num("cooldown_ticks", "cooldown_ticks", 1, 0, 10);

    private final Minecraft mc = Minecraft.getInstance();

    private boolean isCharging = false;
    private int chargeTicks = 0;
    private int cooldownCounter = 0;
    private int previousSlot = -1;
    private InteractionHand tridentHand = InteractionHand.MAIN_HAND;

    private float targetYaw = 0.0f;
    private float targetPitch = 0.0f;
    private boolean hasTargetRotation = false;

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        if (mc.player != null && isCharging) {
            stopCharging(mc.player);
        }
        resetState();
        RotationManager.INSTANCE.clear(this);
    }

    private void resetState() {
        isCharging = false;
        chargeTicks = 0;
        cooldownCounter = 0;
        hasTargetRotation = false;
        restoreHotbarSlot();
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!silentRotation.get() || !hasTargetRotation || mc.player == null) return;

        // Apply smooth or clamped rotation step if in grim mode
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float effectiveYaw = targetYaw;
        float effectivePitch = targetPitch;

        if (grimMode.get()) {
            float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
            float pitchDiff = targetPitch - currentPitch;
            // Grim allows reasonable delta per tick
            float maxYawStep = 45.0f;
            float maxPitchStep = 30.0f;
            effectiveYaw = currentYaw + Mth.clamp(yawDiff, -maxYawStep, maxYawStep);
            effectivePitch = currentPitch + Mth.clamp(pitchDiff, -maxPitchStep, maxPitchStep);
        }

        Rotation rotation = new Rotation(effectiveYaw, effectivePitch, false);
        RotationTarget plan = new RotationTarget(
                rotation,
                mc.player,
                List.of(),
                2,
                0.5f,
                false,
                MovementCorrection.SILENT,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(plan, 50, this);
    }

    @EventHandler
    public void onSync(EventSync event) {
        if (!silentRotation.get() || !hasTargetRotation || mc.player == null) return;
        event.setRotation(targetYaw, targetPitch, true);
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        if (cooldownCounter > 0) {
            cooldownCounter--;
            return;
        }

        // Check environment conditions for Riptide (water, rain, or bubble column)
        if (weatherCheck.get() && !canRiptideInEnvironment(player)) {
            if (isCharging) {
                stopCharging(player);
                resetState();
            }
            return;
        }

        // Find or check trident in hand
        InteractionHand hand = getRiptideHand(player);
        if (hand == null && autoSwap.get()) {
            int slot = findRiptideHotbarSlot(player);
            if (slot != -1) {
                int current = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
                if (previousSlot == -1) {
                    previousSlot = current;
                }
                ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(slot);
                hand = InteractionHand.MAIN_HAND;
            }
        }

        if (hand == null) {
            if (isCharging) {
                stopCharging(player);
                resetState();
            }
            return;
        }

        tridentHand = hand;

        // Calculate flight angles based on mode
        calculateTargetAngles(player);

        if (!isCharging) {
            // Start charging
            mc.gameMode.useItem(player, tridentHand);
            isCharging = true;
            chargeTicks = 0;
        } else {
            chargeTicks++;

            int requiredTicks = Math.max(10, minChargeTicks.get());
            if (chargeTicks >= requiredTicks) {
                // Ensure Grim riptide compliance: player must have charged for >= 10 ticks
                mc.gameMode.releaseUsingItem(player);
                isCharging = false;
                chargeTicks = 0;
                cooldownCounter = cooldownTicks.get();

                // Swapping back if autoSwap was engaged and not charging
                if (autoSwap.get() && previousSlot != -1) {
                    restoreHotbarSlot();
                }
            }
        }
    }

    private void stopCharging(LocalPlayer player) {
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        mc.gameMode.releaseUsingItem(player);
        isCharging = false;
        chargeTicks = 0;
    }

    private void restoreHotbarSlot() {
        if (previousSlot >= 0 && previousSlot < 9 && mc.player != null) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }

    private void calculateTargetAngles(LocalPlayer player) {
        float playerYaw = player.getYRot();
        float calcPitch;
        float calcYaw = playerYaw;

        // Base flight yaw from movement input if moving
        if (player.input != null) {
            net.minecraft.world.phys.Vec2 move = player.input.getMoveVector();
            float forward = move.y;
            float strafe = move.x;
            if (forward != 0.0f || strafe != 0.0f) {
                calcYaw = playerYaw + (float) Math.toDegrees(Mth.atan2(-strafe, forward));
            }
        }

        Mode currentMode = mode.get();
        if (currentMode == Mode.HEIGHT_MAINTENANCE) {
            double currentY = player.getY();
            double targetY = targetHeight.get();
            double diffY = targetY - currentY;

            if (diffY > 5.0) {
                // Below target height by more than 5 blocks -> pitch up
                float factor = (float) Mth.clamp(diffY / 30.0, 0.0, 1.0);
                calcPitch = -45.0f - (15.0f * factor); // -45° to -60°
            } else if (diffY < -5.0) {
                // Above target height by more than 5 blocks -> glide/descend towards target
                float factor = (float) Mth.clamp((-diffY) / 30.0, 0.0, 1.0);
                calcPitch = -10.0f + (25.0f * factor); // -10° to 15°
            } else {
                // Near target height -> optimal forward Riptide glide pitch
                calcPitch = -12.0f; // -10° to -15°
            }
        } else if (currentMode == Mode.FORWARD) {
            calcPitch = -10.0f; // -5° to -15° optimal forward boost
        } else { // UPWARD
            calcPitch = -90.0f; // Straight up
        }

        targetYaw = calcYaw;
        targetPitch = calcPitch;
        hasTargetRotation = true;
    }

    private boolean canRiptideInEnvironment(LocalPlayer player) {
        if (player.isInWater() || player.isUnderWater()) return true;
        if (mc.level != null && mc.level.isRainingAt(player.blockPosition())) return true;
        return false;
    }

    private InteractionHand getRiptideHand(LocalPlayer player) {
        if (isRiptideTrident(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (isRiptideTrident(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private int findRiptideHotbarSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isRiptideTrident(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRiptideTrident(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.TRIDENT) {
            return false;
        }

        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return false;
        }

        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.unwrapKey().isPresent() && holder.unwrapKey().get().equals(Enchantments.RIPTIDE)) {
                return enchantments.getLevel(holder) > 0;
            }
        }

        int level = getEnchantmentLevel(Enchantments.RIPTIDE, stack);
        return level > 0;
    }

    private int getEnchantmentLevel(ResourceKey<Enchantment> key, ItemStack stack) {
        if (mc.level == null || key == null) return 0;
        try {
            Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            Enchantment enchantment = registry.getValue(key);
            if (enchantment != null) {
                Holder<Enchantment> holder = registry.wrapAsHolder(enchantment);
                return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
}
