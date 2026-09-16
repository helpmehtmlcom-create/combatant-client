/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.SlotResult;

import java.util.List;

@ModuleInfo(
        id = "packetexp",
        displayName = "PacketExp",
        category = ModuleCategory.PLAYER,
        description = "Silently mends armor with experience bottles by automatically aiming at feet without camera shake."
)
public class PacketExp extends Module {

    private static final EquipmentSlot[] SCAN_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };

    private final NumberValue<Integer> packetsPerTick =
            num("packets_per_tick", "packets_per_tick", 2, 1, 10);

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @EventHandler(priority = 20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        // Automatically stop when all equipment reaches full durability
        if (isAllEquipmentRepaired()) {
            setEnabled(false);
            return;
        }

        boolean useOffhand = mc.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
        SlotResult hotbarExp = useOffhand ? SlotResult.empty() : InventorySwap.INSTANCE.findHotbar(s -> s.is(Items.EXPERIENCE_BOTTLE));

        if (!useOffhand && !hotbarExp.found()) {
            return;
        }

        float yaw = mc.player.getYRot();
        float pitch = 90.0f; // Aim straight down at feet

        // Aim silently via RotationManager without client camera shake
        RotationManager.INSTANCE.setRotationTarget(
                new RotationTarget(
                        new Rotation(yaw, pitch, true),
                        null,
                        List.of(),
                        1,
                        0.0f,
                        false,
                        MovementCorrection.SILENT,
                        null
                ),
                50,
                this
        );

        int count = packetsPerTick.get();
        InteractionHand hand = useOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (useOffhand) {
            for (int i = 0; i < count; i++) {
                InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
        } else {
            boolean leased = InventorySwap.INSTANCE.leaseHotbar(this, hotbarExp.slot(), 1);
            if (leased) {
                for (int i = 0; i < count; i++) {
                    InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                    mc.getConnection().send(new ServerboundSwingPacket(hand));
                }
                InventorySwap.INSTANCE.releaseHotbar(this);
            }
        }
    }

    private boolean isAllEquipmentRepaired() {
        if (mc.player == null) return true;
        for (EquipmentSlot slot : SCAN_SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
                return false;
            }
        }
        return true;
    }
}
