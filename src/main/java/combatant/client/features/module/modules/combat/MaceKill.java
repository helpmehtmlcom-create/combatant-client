/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

//Такая хуйня на более менее ач работать не будет
// thx liquidbounce
@ModuleInfo(
        id = "macekill",
        displayName = "MaceKill",
        description = "Calculates and executes fall-distance smash attacks with the Mace for lethal one-shot damage.",
        category = ModuleCategory.COMBAT
)
public final class MaceKill extends Module {
    public static boolean cancelCrit;

    private final Minecraft mc = Minecraft.getInstance();
    private final NumberValue<Integer> fallHeight =
            num("height", 22, 1, 170);
    private final BooleanValue silentSwitch =
            bool("macekill_silent_switch", "silent_switch", true);
    @Override
    public void onEnable() {
        cancelCrit = false;
    }

    @Override
    public void onDisable() {
        cancelCrit = false;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event == null || !isEnabled()) return;
        if (!(event.getPacket() instanceof ServerboundInteractPacket packet)) return;
        if (!isAttack(packet)) return;

        Entity ent = getEntity(packet);
        if (ent == null || !ent.isAlive() || ent.isRemoved() || cancelCrit) return;
        if (mc.player == null || mc.player.isDeadOrDying()) return;

        boolean holdingMace = isHoldingMace();
        if (!holdingMace && silentSwitch.get()) {
            int maceSlot = findMaceSlot();
            if (maceSlot != -1) {
                InventorySwap.INSTANCE.leaseHotbar(this, maceSlot, 2);
                holdingMace = true;
            }
        }

        if (!holdingMace) return;

        doCrit();
    }

    private int findMaceSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.MACE)) {
                return i;
            }
        }
        return -1;
    }
    private void doCrit() {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null || mc.player.isDeadOrDying()) return;
        int height = determineHeight();
        if (height <= 0) return;

        // Paper/Spigot exploit: send multiple groundless packets for large heights
        if (height > 10) {
            int repeats = (int) Math.ceil(Math.abs(height / 10.0));
            for (int i = 0; i < repeats; i++) {
                sendOnGroundOnly(false);
            }
        } else {
            // Do it at least twice to neutralize horizontal distance
            boolean onGround = mc.player.onGround();
            sendOnGroundOnly(onGround);
            sendOnGroundOnly(onGround);
        }

        // Teleport to the calculated height
        sendPosition(mc.player.getX(), mc.player.getY() + height, mc.player.getZ(), false);
        // Ensure the player returns to ground state.
        sendPosition(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false);
    }

    private void sendOnGroundOnly(boolean onGround) {
        if (mc == null || mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().send(
                new ServerboundMovePlayerPacket.StatusOnly(onGround, mc.player.horizontalCollision)
        );
    }

    private void sendPosition(double x, double y, double z, boolean onGround) {
        if (mc == null || mc.player == null || mc.getConnection() == null) return;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
        mc.getConnection().send(
                new ServerboundMovePlayerPacket.Pos(
                        x, y, z, onGround, mc.player.horizontalCollision
                )
        );
    }

    private int determineHeight() {
        if (mc == null || mc.player == null || mc.level == null) return 0;

        AABB bb = mc.player.getBoundingBox();
        int max = fallHeight.get();
        for (int i = max; i >= 1; i--) {
            AABB shifted = bb.move(0.0, i, 0.0);
            if (isBoxClear(shifted)) {
                return i;
            }
        }
        return 0;
    }

    private boolean isBoxClear(AABB box) {
        if (mc == null || mc.player == null || mc.level == null) return false;
        for (VoxelShape shape : mc.level.getBlockCollisions(mc.player, box)) {
            if (!shape.isEmpty()) return false;
        }
        return true;
    }

    private boolean isHoldingMace() {
        if (mc == null || mc.player == null) return false;
        ItemStack mainHand = mc.player.getMainHandItem();
        return mainHand != null && mainHand.getItem() == Items.MACE;
    }

    private Entity getEntity(ServerboundInteractPacket packet) {
        if (mc == null || mc.level == null) return null;
        int id = packet.entityId();
        return mc.level.getEntity(id);
    }

    private boolean isAttack(ServerboundInteractPacket packet) {
        return packet.hand() == null && packet.location() == null;
    }

}
