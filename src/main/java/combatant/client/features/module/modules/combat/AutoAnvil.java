/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "autoanvil",
        displayName = "AutoAnvil",
        category = ModuleCategory.COMBAT
)
public final class AutoAnvil extends Module {

    private final NumberValue<Double> targetRange = num("target_range", 6.0, 2.0, 8.0);
    private final NumberValue<Integer> dropHeight = num("drop_height", 3, 2, 8);
    private final BooleanValue rotate = bool("rotate", true);

    private final Minecraft mc = Minecraft.getInstance();

    @EventHandler
    public void onTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.gameMode == null) return;
        LocalPlayer player = mc.player;

        int anvilSlot = findAnvilSlot();
        if (anvilSlot == -1) return;

        Player target = findTarget();
        if (target == null) return;

        BlockPos targetPos = target.blockPosition();
        BlockPos placePos = targetPos.above(dropHeight.get());

        if (mc.level.getBlockState(placePos).isAir()) {
            int prev = ((PlayerInventoryAccessor) player.getInventory()).combatant$getSelectedSlot();
            ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(anvilSlot);

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(placePos), Direction.UP, placePos.below(), false
            );
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
            player.swing(InteractionHand.MAIN_HAND);

            ((PlayerInventoryAccessor) player.getInventory()).combatant$setSelectedSlot(prev);
        }
    }

    private int findAnvilSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.ANVIL) || stack.is(Items.CHIPPED_ANVIL) || stack.is(Items.DAMAGED_ANVIL)) {
                return i;
            }
        }
        return -1;
    }

    private Player findTarget() {
        if (mc.player == null || mc.level == null) return null;
        Player best = null;
        double bestDist = Double.MAX_VALUE;

        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isSpectator() || p.isDeadOrDying()) continue;
            double d = mc.player.distanceTo(p);
            if (d <= targetRange.get() && d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }
}
