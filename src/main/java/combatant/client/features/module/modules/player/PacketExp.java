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
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.util.LegacyHudNotifier;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.player.InteractionUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

@ModuleInfo(
        id = "packetexp",
        displayName = "PacketExp",
        category = ModuleCategory.PLAYER,
        description = "Silently mends armor with experience bottles at high speed"
)
public class PacketExp extends Module {

    private final EnumValue<Mode> mode =
            enumSetting("packetExpMode", "mode", Mode.SILENT, Mode.values());
    private final NumberValue<Integer> throwDelay =
            num("packetExpThrowDelay", "throw_delay", 0, 0, 5);
    private final NumberValue<Integer> packetsPerTick =
            num("packetExpPacketsPerTick", "packets_per_tick", 2, 1, 10);
    private final NumberValue<Integer> minArmorDura =
            num("packetExpMinArmorDura", "min_armor_dura", 85, 1, 99);
    private final BooleanValue silentPitch =
            bool("packetExpSilentPitch", "silent_pitch", true);
    private final BooleanValue autoDisable =
            bool("packetExpAutoDisable", "auto_disable", true);
    private final BooleanValue offhandOverride =
            bool("packetExpOffhandOverride", "offhand_override", false);

    private final Minecraft mc = Minecraft.getInstance();
    private int delayTimer = 0;

    public PacketExp() {
    }

    @Override
    public void onEnable() {
        delayTimer = 0;
    }

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
        delayTimer = 0;
    }

    @EventHandler(priority = 20)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;

        double lowestDura = getLowestArmorDurability();
        if (lowestDura >= minArmorDura.get()) {
            if (autoDisable.get()) {
                setEnabled(false);
                sendNotification("§7[§bPacketExp§7] §aAll armor repaired to §f" + minArmorDura.get() + "%§a, auto-disabling.");
            }
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }
        delayTimer = throwDelay.get();

        boolean useOffhand = offhandOverride.get() && mc.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
        int expSlot = useOffhand ? -1 : findExpBottleInHotbar();

        if (!useOffhand && expSlot == -1) {
            return;
        }

        float pitch = silentPitch.get() ? 90.0f : mc.player.getXRot();
        float yaw = mc.player.getYRot();

        if (silentPitch.get()) {
            RotationManager.INSTANCE.setRotationTarget(
                    new RotationTarget(
                            new Rotation(yaw, 90.0f, true),
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
        }

        int count = packetsPerTick.get();
        InteractionHand hand = useOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;

        if (useOffhand) {
            for (int i = 0; i < count; i++) {
                InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                mc.getConnection().send(new ServerboundSwingPacket(hand));
            }
        } else {
            Mode currentMode = mode.get() != null ? mode.get() : Mode.SILENT;
            switch (currentMode) {
                case PACKET -> {
                    int prev = ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$getSelectedSlot();
                    if (prev != expSlot) {
                        mc.getConnection().send(new ServerboundSetCarriedItemPacket(expSlot));
                    }
                    for (int i = 0; i < count; i++) {
                        InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                        mc.getConnection().send(new ServerboundSwingPacket(hand));
                    }
                    if (prev != expSlot) {
                        mc.getConnection().send(new ServerboundSetCarriedItemPacket(prev));
                    }
                }
                case SILENT -> {
                    InventorySwap.INSTANCE.withSwap(expSlot, InventorySwapMode.SILENT, () -> {
                        for (int i = 0; i < count; i++) {
                            InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                            mc.getConnection().send(new ServerboundSwingPacket(hand));
                        }
                    });
                }
                case FAST -> {
                    InventorySwap.INSTANCE.selectHotbar(expSlot);
                    for (int i = 0; i < count; i++) {
                        InteractionUtil.sendSequencedPacket(id -> new ServerboundUseItemPacket(hand, id, yaw, pitch));
                        mc.getConnection().send(new ServerboundSwingPacket(hand));
                    }
                }
            }
        }
    }

    private double getLowestArmorDurability() {
        if (mc.player == null) return 100.0;
        double lowest = 100.0;
        boolean hasArmor = false;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) continue;
            hasArmor = true;
            double duraPercent = (1.0 - ((double) stack.getDamageValue() / (double) stack.getMaxDamage())) * 100.0;
            if (duraPercent < lowest) {
                lowest = duraPercent;
            }
        }

        return hasArmor ? lowest : 100.0;
    }

    private int findExpBottleInHotbar() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.EXPERIENCE_BOTTLE)) {
                return i;
            }
        }
        return -1;
    }

    private void sendNotification(String message) {
        LegacyHudNotifier.show(message);
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        }
    }

    public enum Mode implements EnumValue.IdProvider {
        FAST("fast"),
        SILENT("silent"),
        PACKET("packet");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
