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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "echestfarmer",
        displayName = "EChestFarmer",
        aliases = {"ObsidianFarmer", "EChestMiner"},
        category = ModuleCategory.PLAYER,
        description = "Automatically places and breaks Ender Chests to farm obsidian or chests."
)
public final class EChestFarmer extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<FarmMode> mode =
            enumSetting("eChestFarmerMode", "mode", FarmMode.OBSIDIAN, FarmMode.values());

    private final BooleanValue autoDisable =
            bool("eChestFarmerAutoDisable", "auto_disable", true);

    private final NumberValue<Integer> delay =
            num("eChestFarmerDelay", "delay", 2, 0, 10);

    private final BooleanValue silentTool =
            bool("eChestFarmerSilentTool", "silent_tool", true);

    private BlockPos farmPos = null;
    private float breakProgress = 0.0f;
    private boolean isMining = false;
    private int cooldown = 0;

    @Override
    public void onDisable() {
        if (isMining && farmPos != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    farmPos,
                    Direction.UP
            ));
        }
        farmPos = null;
        breakProgress = 0.0f;
        isMining = false;
        cooldown = 0;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        farmPos = player.blockPosition().relative(player.getDirection());
        BlockState state = mc.level.getBlockState(farmPos);

        // 1. If empty space, place an Ender Chest
        if (state.canBeReplaced()) {
            int echestSlot = findEChestSlot(player);
            if (echestSlot == -1) {
                if (autoDisable.get()) {
                    setEnabled(false);
                }
                return;
            }

            InventorySwap.INSTANCE.leaseHotbar(this, echestSlot, 2);

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(farmPos),
                    Direction.UP,
                    farmPos.below(),
                    false
            );

            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);
            cooldown = delay.get();
            breakProgress = 0.0f;
            isMining = false;
            return;
        }

        // 2. If it is an Ender Chest, mine it
        if (state.is(Blocks.ENDER_CHEST)) {
            if (!isMining) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        farmPos,
                        Direction.UP
                ));
                isMining = true;
            }

            float delta = state.getDestroyProgress(player, mc.level, farmPos);
            breakProgress += delta;

            if (breakProgress >= 1.0f) {
                if (silentTool.get()) {
                    int toolSlot = findBestPickaxe(player);
                    if (toolSlot != -1) {
                        InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
                    }
                }

                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                        farmPos,
                        Direction.UP
                ));
                player.swing(InteractionHand.MAIN_HAND);

                breakProgress = 0.0f;
                isMining = false;
                cooldown = delay.get();
            }
        }
    }

    private int findEChestSlot(LocalPlayer player) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.ENDER_CHEST)) {
                return i;
            }
        }
        return -1;
    }

    private int findBestPickaxe(LocalPlayer player) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.PICKAXES)) {
                boolean hasSilkTouch = hasSilkTouch(stack);
                if (mode.get() == FarmMode.OBSIDIAN && hasSilkTouch) {
                    continue; // Skip silk touch when farming raw obsidian
                }
                if (mode.get() == FarmMode.SILENT_SILK && !hasSilkTouch) {
                    continue;
                }

                float speed = stack.getDestroySpeed(Blocks.ENDER_CHEST.defaultBlockState());
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        if (bestSlot == -1) {
            // Fallback to any pickaxe
            for (int i = 0; i < 9; i++) {
                if (player.getInventory().getItem(i).is(ItemTags.PICKAXES)) {
                    return i;
                }
            }
        }

        return bestSlot;
    }

    private boolean hasSilkTouch(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null) return false;
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().getRegisteredName().contains("silk_touch")) {
                return true;
            }
        }
        return false;
    }

    public enum FarmMode {
        OBSIDIAN,
        SILENT_SILK
    }
}
