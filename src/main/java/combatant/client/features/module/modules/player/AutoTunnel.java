/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

@ModuleInfo(
        id = "autotunnel",
        displayName = "AutoTunnel",
        aliases = {"Tunnel", "HighwayTunnel"},
        category = ModuleCategory.PLAYER,
        description = "Automatically mines a 1x2 tunnel in the direction you are facing."
)
public final class AutoTunnel extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue walkForward =
            bool("autoTunnelWalkForward", "walk_forward", true);

    private final BooleanValue silentTool =
            bool("autoTunnelSilentTool", "silent_tool", true);

    private final BooleanValue render =
            bool("autoTunnelRender", "render", true);

    private BlockPos miningPos = null;
    private float breakProgress = 0.0f;
    private boolean isMining = false;

    @Override
    public void onDisable() {
        if (isMining && miningPos != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    miningPos,
                    Direction.UP
            ));
        }
        miningPos = null;
        breakProgress = 0.0f;
        isMining = false;
        InventorySwap.INSTANCE.releaseHotbar(this);

        if (mc.options != null) {
            mc.options.keyUp.setDown(false);
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            return;
        }

        Direction dir = player.getDirection();
        BlockPos feet = player.blockPosition();

        BlockPos targetHead = feet.relative(dir).above();
        BlockPos targetFeet = feet.relative(dir);

        BlockState headState = mc.level.getBlockState(targetHead);
        BlockState feetState = mc.level.getBlockState(targetFeet);

        BlockPos nextTarget = null;
        if (!headState.isAir() && headState.getDestroySpeed(mc.level, targetHead) >= 0) {
            nextTarget = targetHead;
        } else if (!feetState.isAir() && feetState.getDestroySpeed(mc.level, targetFeet) >= 0) {
            nextTarget = targetFeet;
        }

        if (nextTarget == null) {
            // Path ahead is clear! Walk forward
            miningPos = null;
            breakProgress = 0.0f;
            isMining = false;

            if (walkForward.get()) {
                mc.options.keyUp.setDown(true);
            }
            return;
        }

        // Target found! Stop walking and mine
        if (walkForward.get()) {
            mc.options.keyUp.setDown(false);
        }

        if (miningPos == null || !miningPos.equals(nextTarget)) {
            miningPos = nextTarget;
            breakProgress = 0.0f;
            isMining = false;
        }

        BlockState state = mc.level.getBlockState(miningPos);

        if (!isMining) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    miningPos,
                    dir.getOpposite()
            ));
            isMining = true;
        }

        float delta = state.getDestroyProgress(player, mc.level, miningPos);
        breakProgress += delta;

        if (breakProgress >= 1.0f) {
            if (silentTool.get()) {
                int toolSlot = findBestTool(state, player);
                if (toolSlot != -1) {
                    InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
                }
            }

            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    miningPos,
                    dir.getOpposite()
            ));
            player.swing(InteractionHand.MAIN_HAND);

            miningPos = null;
            breakProgress = 0.0f;
            isMining = false;
        }
    }

    private int findBestTool(BlockState state, LocalPlayer player) {
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                float speed = stack.getDestroySpeed(state);
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    bestSlot = i;
                }
            }
        }

        return bestSlot;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || miningPos == null) return;

        float p = Math.min(1.0f, Math.max(0.0f, breakProgress));
        int r = (int) ((1.0f - p) * 255);
        int g = (int) (p * 255);
        int b = 40;

        int fillArgb = (0x55 << 24) | (r << 16) | (g << 8) | b;
        int lineArgb = (0xFF << 24) | (r << 16) | (g << 8) | b;

        AABB box = new AABB(miningPos);
        addFilledBox(renderer, box, fillArgb);
        addOutlineBox(renderer, box, lineArgb);
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);

        renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
    }
}
