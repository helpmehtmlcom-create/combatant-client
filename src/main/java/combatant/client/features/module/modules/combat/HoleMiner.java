/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

@ModuleInfo(
        id = "holeminer",
        displayName = "HoleMiner",
        aliases = {"HoleBreaker", "HoleNuker"},
        category = ModuleCategory.COMBAT,
        description = "Automatically packet-mines obsidian hole blocks around opponents."
)
public final class HoleMiner extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("holeMinerRange", "range", CommonSettingSchemas.COMBAT_RANGE, 5.0, 2.0, 7.0);

    private final NumberValue<Double> enemyRange =
            num("holeMinerEnemyRange", "enemy_range", 6.0, 2.0, 10.0);

    private final BooleanValue silentTool =
            bool("holeMinerSilentTool", "silent_tool", true);

    private final BooleanValue autoDisable =
            bool("holeMinerAutoDisable", "auto_disable", false);

    private final BooleanValue render =
            bool("holeMinerRender", "render", true);

    private final RGBAColorValue fillColor =
            color("holeMinerFillColor", "fill_color", "#FF005544");

    private final RGBAColorValue lineColor =
            color("holeMinerLineColor", "line_color", "#FF3300FF");

    private BlockPos targetBlock = null;
    private Direction targetDir = Direction.UP;
    private float breakProgress = 0.0f;
    private boolean isMining = false;

    @Override
    public void onDisable() {
        if (isMining && targetBlock != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    targetBlock,
                    targetDir
            ));
        }
        targetBlock = null;
        breakProgress = 0.0f;
        isMining = false;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.getConnection() == null) {
            targetBlock = null;
            return;
        }

        if (targetBlock == null || level.getBlockState(targetBlock).isAir()) {
            targetBlock = findHoleBlock(player, level);
            breakProgress = 0.0f;
            isMining = false;

            if (targetBlock == null) {
                if (autoDisable.get()) {
                    setEnabled(false);
                }
                return;
            }
        }

        BlockState state = level.getBlockState(targetBlock);
        if (state.isAir()) {
            targetBlock = null;
            breakProgress = 0.0f;
            isMining = false;
            return;
        }

        if (!isMining) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    targetBlock,
                    targetDir
            ));
            isMining = true;
        }

        float delta = state.getDestroyProgress(player, level, targetBlock);
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
                    targetBlock,
                    targetDir
            ));
            player.swing(InteractionHand.MAIN_HAND);

            targetBlock = null;
            breakProgress = 0.0f;
            isMining = false;

            if (autoDisable.get()) {
                setEnabled(false);
            }
        }
    }

    private BlockPos findHoleBlock(LocalPlayer player, ClientLevel level) {
        Player opponent = findTargetOpponent(player);
        if (opponent == null) return null;

        BlockPos oppFeet = opponent.blockPosition();
        double maxDistSq = range.get() * range.get();

        BlockPos bestBlock = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos check = oppFeet.relative(dir);
            BlockState state = level.getBlockState(check);

            if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN) || state.is(Blocks.ENDER_CHEST) || state.is(Blocks.RESPAWN_ANCHOR)) {
                double distSq = player.distanceToSqr(check.getX() + 0.5, check.getY() + 0.5, check.getZ() + 0.5);
                if (distSq <= maxDistSq && distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestBlock = check;
                    targetDir = dir.getOpposite();
                }
            }
        }

        return bestBlock;
    }

    private Player findTargetOpponent(LocalPlayer player) {
        if (mc.level == null) return null;

        Player closest = null;
        double closestDistSq = enemyRange.get() * enemyRange.get();

        for (Player p : mc.level.players()) {
            if (p.equals(player) || p.isSpectator() || p.isCreative()) continue;
            String name = p.getName().getString();
            if (PlayerRelations.get().getFriends().contains(name)) continue;

            double distSq = player.distanceToSqr(p);
            if (distSq < closestDistSq) {
                closest = p;
                closestDistSq = distSq;
            }
        }

        return closest;
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
        if (!isEnabled() || !render.get() || targetBlock == null) return;

        AABB box = new AABB(targetBlock);
        addFilledBox(renderer, box, fillColor.getArgb());
        addOutlineBox(renderer, box, lineColor.getArgb());
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
