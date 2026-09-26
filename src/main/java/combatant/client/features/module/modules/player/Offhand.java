/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@ModuleInfo(
        id = "offhand",
        displayName = "Offhand",
        aliases = {"AutoOffhand", "OffhandManager"},
        category = ModuleCategory.PLAYER,
        description = "Intelligently equips items (Totem, Gapple, Crystal, Shield) into your offhand."
)
public final class Offhand extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<OffhandMode> defaultItem =
            enumSetting("offhandDefaultItem", "default_item", OffhandMode.TOTEM, OffhandMode.values());

    private final NumberValue<Float> healthThreshold =
            num("offhandHealthThreshold", "health_threshold", 12.0f, 1.0f, 20.0f);

    private final BooleanValue rightClickGapple =
            bool("offhandRightClickGapple", "right_click_gapple", true);

    private final BooleanValue crystalOnSword =
            bool("offhandCrystalOnSword", "crystal_on_sword", false);

    private final BooleanValue elytraTotem =
            bool("offhandElytraTotem", "elytra_totem", true);

    private final NumberValue<Float> fallDistance =
            num("offhandFallDistance", "fall_distance", 8.0f, 3.0f, 20.0f);

    private boolean swapPending = false;

    @Override
    public void onDisable() {
        swapPending = false;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || swapPending) {
            return;
        }

        OffhandMode targetMode = determineTargetMode(player);
        if (isAlreadyHolding(player, targetMode)) {
            return;
        }

        int slot = findItemSlot(player, targetMode);
        if (slot == -1) return;

        swapPending = true;
        boolean accepted = InventorySwap.INSTANCE.swapInventoryToOffhand(slot, () -> swapPending = false);
        if (!accepted) {
            swapPending = false;
        }
    }

    private OffhandMode determineTargetMode(LocalPlayer player) {
        float health = player.getHealth() + player.getAbsorptionAmount();

        if (elytraTotem.get() && player.isFallFlying()) {
            return OffhandMode.TOTEM;
        }

        if (player.fallDistance >= fallDistance.get()) {
            return OffhandMode.TOTEM;
        }

        if (health <= healthThreshold.get()) {
            return OffhandMode.TOTEM;
        }

        if (rightClickGapple.get() && mc.options.keyUse.isDown() && !(player.getMainHandItem().getItem() instanceof BlockItem)) {
            return OffhandMode.GAPPLE;
        }

        if (crystalOnSword.get() && player.getMainHandItem().is(ItemTags.SWORDS)) {
            return OffhandMode.CRYSTAL;
        }

        return defaultItem.get();
    }

    private boolean isAlreadyHolding(LocalPlayer player, OffhandMode mode) {
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) return false;

        return switch (mode) {
            case TOTEM -> offhand.is(Items.TOTEM_OF_UNDYING);
            case GAPPLE -> offhand.is(Items.ENCHANTED_GOLDEN_APPLE) || offhand.is(Items.GOLDEN_APPLE);
            case CRYSTAL -> offhand.is(Items.END_CRYSTAL);
            case SHIELD -> offhand.is(Items.SHIELD);
        };
    }

    private int findItemSlot(LocalPlayer player, OffhandMode mode) {
        // Search hotbar first, then main inventory
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            boolean matches = switch (mode) {
                case TOTEM -> stack.is(Items.TOTEM_OF_UNDYING);
                case GAPPLE -> stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.GOLDEN_APPLE);
                case CRYSTAL -> stack.is(Items.END_CRYSTAL);
                case SHIELD -> stack.is(Items.SHIELD);
            };

            if (matches) {
                return i;
            }
        }

        return -1;
    }

    public enum OffhandMode {
        TOTEM,
        GAPPLE,
        CRYSTAL,
        SHIELD
    }
}
