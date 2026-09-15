/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "fastplace",
        displayName = "FastPlace",
        category = ModuleCategory.PLAYER,
        aliases = {"fastuse"}
)
public final class FastPlace extends Module {

    private final EnumValue<FastPlaceMode> mode =
            enumSetting("fastplaceMode", "mode", FastPlaceMode.CRYSTALS, FastPlaceMode.values());
    private final NumberValue<Integer> delay =
            num("fastplaceDelay", "delay", 0, 0, 4);

    private final Minecraft mc = Minecraft.getInstance();

    public FastPlace() {
    }

    public EnumValue<FastPlaceMode> getMode() {
        return mode;
    }

    public NumberValue<Integer> getDelay() {
        return delay;
    }

    @Override
    public void onTick() {
        tickFastPlace();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        tickFastPlace();
    }

    private void tickFastPlace() {
        if (!isEnabled() || mc == null || mc.player == null) {
            return;
        }

        if (!isHoldingTargetItem()) {
            return;
        }

        if (!(mc instanceof MinecraftAccessor accessor)) {
            return;
        }

        int targetDelay = delay.get() != null ? delay.get() : 0;
        int currentCooldown = accessor.combatant$getItemUseCooldown();

        if (currentCooldown > targetDelay) {
            accessor.combatant$setItemUseCooldown(targetDelay);
        }

        if (targetDelay == 0 && mc.options != null && mc.options.keyUse != null && mc.options.keyUse.isDown()) {
            accessor.combatant$setItemUseCooldown(0);
        }
    }

    private boolean isHoldingTargetItem() {
        if (mc.player == null) {
            return false;
        }
        FastPlaceMode currentMode = mode.get() != null ? mode.get() : FastPlaceMode.CRYSTALS;
        return matchesItem(mc.player.getMainHandItem(), currentMode)
                || matchesItem(mc.player.getOffhandItem(), currentMode);
    }

    private boolean matchesItem(ItemStack stack, FastPlaceMode currentMode) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return switch (currentMode) {
            case ALL -> true;
            case CRYSTALS -> item == Items.END_CRYSTAL;
            case EXP -> item == Items.EXPERIENCE_BOTTLE;
            case FIREWORKS -> item == Items.FIREWORK_ROCKET;
            case BLOCKS -> item instanceof BlockItem;
        };
    }

    public enum FastPlaceMode implements EnumValue.IdProvider {
        ALL("all"),
        CRYSTALS("crystals"),
        EXP("exp"),
        FIREWORKS("fireworks"),
        BLOCKS("blocks");

        private final String id;

        FastPlaceMode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String id() {
            return id;
        }
    }
}
