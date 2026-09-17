/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.SlotResult;

@ModuleInfo(
        id = "automend",
        displayName = "AutoMend",
        aliases = {"packetexp", "autoexp", "fastmending", "mending"},
        category = ModuleCategory.PLAYER,
        description = "Automatically scans armor and held items for Mending, silently throwing experience bottles until fully repaired."
)
public final class AutoMend extends Module {

    public enum Mode {
        PACKET,
        VANILLA
    }

    private static final EquipmentSlot[] SCAN_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    private final EnumValue<Mode> mode =
            description(enumSetting("automendMode", "mode", Mode.PACKET, Mode.values()),
                    "Method used to throw experience bottles. Packet mode allows throwing multiple bottles per tick silently.");

    private final NumberValue<Integer> packetsPerTick =
            description(visibleWhen(num("automendPacketsPerTick", "packets_per_tick", 2, 1, 10),
                            () -> mode.get() == Mode.PACKET),
                    "Number of experience bottles thrown per tick in packet mode.");

    private final NumberValue<Integer> repairThreshold =
            description(num("automendRepairThreshold", "repair_threshold", 100, 10, 100),
                    "Target durability percentage to repair equipment and held items up to.");

    private final BooleanValue onlyMending =
            description(bool("automendOnlyMending", "only_mending", true),
                    "Only repair items enchanted with Mending.");

    private final BooleanValue silentRotation =
            description(bool("automendSilentRotation", "silent_rotation", true),
                    "Silently aims experience bottles straight down at your feet without client camera shake.");

    private final BooleanValue autoDisable =
            description(bool("automendAutoDisable", "auto_disable", false),
                    "Automatically toggles off the module once all equipment reaches the repair threshold.");

    private final BooleanValue preferOffhand =
            description(bool("automendPreferOffhand", "prefer_offhand", true),
                    "Throws experience bottles from the offhand if present.");

    private final BooleanValue swing =
            description(bool("automendSwing", "swing", true),
                    "Renders client hand swing and sends arm swing packets when throwing.");

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onDisable() {
        InventorySwap.INSTANCE.releaseHotbar(this);
        RotationManager.INSTANCE.clear(this);
    }

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getConnection() == null) return;
        LocalPlayer player = mc.player;

        boolean needsRepair = false;

        for (EquipmentSlot slot : SCAN_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;
            if (onlyMending.get() && !hasMending(stack)) continue;

            int maxDamage = stack.getMaxDamage();
            int currentDamage = stack.getDamageValue();
            if (currentDamage > 0) {
                double duraPct = 100.0 * (maxDamage - currentDamage) / (double) maxDamage;
                if (duraPct < repairThreshold.get()) {
                    needsRepair = true;
                    break;
                }
            }
        }

        if (!needsRepair) {
            InventorySwap.INSTANCE.releaseHotbar(this);
            if (autoDisable.get()) {
                setEnabled(false);
            }
            return;
        }

        boolean useOffhand = preferOffhand.get() && player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
        SlotResult hotbarExp = useOffhand ? SlotResult.empty() : InventorySwap.INSTANCE.findHotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));

        if (!useOffhand && !hotbarExp.found()) {
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }

        float yaw = player.getYRot();
        float pitch = 90.0f;

        if (silentRotation.get()) {
            Rotation rot = new Rotation(yaw, pitch, false);
            RotationManager.INSTANCE.snapServerRotation(rot, 45, this, 1);
        }

        InteractionHand hand = useOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (useOffhand) {
            throwBottles(player, hand, yaw, pitch);
        } else {
            boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, hotbarExp.slot(), 1);
            if (leased) {
                throwBottles(player, hand, yaw, pitch);
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private void throwBottles(LocalPlayer player, InteractionHand hand, float yaw, float pitch) {
        if (mode.get() == Mode.PACKET) {
            int count = packetsPerTick.get();
            for (int i = 0; i < count; i++) {
                InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                if (swing.get()) {
                    mc.getConnection().send(new ServerboundSwingPacket(hand));
                }
            }
            if (swing.get()) {
                player.swing(hand);
            }
        } else {
            if (mc.gameMode != null) {
                mc.gameMode.useItem(player, hand);
                if (swing.get()) {
                    player.swing(hand);
                }
            }
        }
    }

    private boolean hasMending(ItemStack stack) {
        if (mc.level == null || stack == null || stack.isEmpty()) return false;
        Registry<Enchantment> registry = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment mending = registry.getValue(Enchantments.MENDING);
        if (mending == null) return false;
        Holder<Enchantment> holder = registry.wrapAsHolder(mending);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0;
    }
}
