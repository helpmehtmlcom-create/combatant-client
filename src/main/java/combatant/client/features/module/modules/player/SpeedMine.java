/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.block.mining.MiningDamageCalculator;

@ModuleInfo(
        id = "speedmine",
        displayName = "SpeedMine",
        category = ModuleCategory.PLAYER,
        description = "Accelerates block breaking with custom packet speeds, instant rebreaking, and damage modifiers."
)
public final class SpeedMine extends Module {

    public enum Mode {
        PACKET,
        DAMAGE,
        INSTANT
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = enumSetting("speedmineMode", "mode", Mode.PACKET, Mode.values());
    private final NumberValue<Float> speed = num("speedmineSpeed", "speed", 1.0f, 0.1f, 3.0f);
    private final NumberValue<Float> breakThreshold = num("speedmineBreakThreshold", "threshold", 1.0f, 0.7f, 1.0f);
    private final NumberValue<Float> startProgress = num("speedmineStartProgress", "start_progress", 0.0f, 0.0f, 0.9f);
    private final BooleanValue instantRebreak = bool("speedmineInstantRebreak", "instant_rebreak", true);
    private final BooleanValue silentSwitch = bool("speedmineSilentSwitch", "silent_switch", true);
    private final BooleanValue autoSwitch = bool("speedmineAutoSwitch", "auto_switch", false);
    private final BooleanValue resetDelay = bool("speedmineResetDelay", "reset_delay", true);
    private final NumberValue<Float> range = num("speedmineRange", "range", 5.5f, 2.0f, 7.0f);
    private final BooleanValue swing = bool("speedmineSwing", "swing", true);
    private final BooleanValue render = bool("speedmineRender", "render", true);
    private final RGBAColorValue fillColor = color("speedmineFillColor", "#285A9C55");
    private final RGBAColorValue lineColor = color("speedmineLineColor", "#FF9BE4FF");

    private BlockPos miningPos = null;
    private Direction miningSide = Direction.UP;
    private float progress = 0.0f;

    private BlockPos rebreakPos = null;
    private Direction rebreakSide = Direction.UP;

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        if (miningPos != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    miningPos,
                    miningSide
            ));
        }
        miningPos = null;
        miningSide = Direction.UP;
        progress = 0.0f;
        rebreakPos = null;
        rebreakSide = Direction.UP;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    public boolean shouldResetDelay() {
        return isEnabled() && resetDelay.get();
    }

    public float getDamageMultiplier() {
        if (!isEnabled()) return 1.0f;
        return mode.get() == Mode.DAMAGE ? speed.get() : 1.0f;
    }

    public boolean onStartDestroyBlock(BlockPos pos, Direction side) {
        if (!isEnabled() || mc.player == null || mc.level == null) return false;
        if (pos == null || side == null) return false;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0) {
            return false;
        }

        if (mode.get() == Mode.INSTANT) {
            sendInstantBreak(pos, side);
            return true;
        }

        if (mode.get() == Mode.PACKET) {
            if (pos.equals(miningPos)) {
                return true;
            }
            startMining(pos, side);
            return true;
        }

        return false;
    }

    public void startMining(BlockPos pos, Direction side) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) return;
        if (pos == null) return;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0) {
            return;
        }

        if (miningPos != null && !miningPos.equals(pos)) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    miningPos,
                    miningSide
            ));
        }

        miningPos = pos.immutable();
        miningSide = side != null ? side : Direction.UP;
        progress = startProgress.get();

        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                miningPos,
                miningSide
        ));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                miningPos,
                miningSide
        ));

        if (swing.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void sendInstantBreak(BlockPos pos, Direction side) {
        if (mc.getConnection() == null || mc.player == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                pos,
                side
        ));
        mc.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                pos,
                side
        ));
        if (swing.get()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        LocalPlayer player = mc.player;

        // Check instant rebreak target
        if (instantRebreak.get() && rebreakPos != null) {
            BlockState rebreakState = mc.level.getBlockState(rebreakPos);
            if (!rebreakState.isAir() && rebreakState.getBlock() != Blocks.BEDROCK) {
                if (player.getEyePosition().distanceTo(Vec3.atCenterOf(rebreakPos)) <= range.get()) {
                    int toolSlot = findBestHotbarTool(rebreakPos);
                    if (toolSlot >= 0 && silentSwitch.get()) {
                        InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
                    } else if (toolSlot >= 0 && autoSwitch.get()) {
                        InventorySwap.INSTANCE.selectHotbar(toolSlot);
                    }
                    mc.getConnection().send(new ServerboundPlayerActionPacket(
                            ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            rebreakPos,
                            rebreakSide
                    ));
                    if (swing.get()) {
                        player.swing(InteractionHand.MAIN_HAND);
                    }
                    return;
                }
            }
        }

        if (miningPos == null) return;

        double dist = player.getEyePosition().distanceTo(Vec3.atCenterOf(miningPos));
        if (dist > range.get()) {
            resetMining();
            return;
        }

        BlockState state = mc.level.getBlockState(miningPos);
        if (state.isAir()) {
            if (instantRebreak.get()) {
                rebreakPos = miningPos;
                rebreakSide = miningSide;
            }
            miningPos = null;
            progress = 0.0f;
            InventorySwap.INSTANCE.releaseHotbar(this);
            return;
        }

        int bestTool = findBestHotbarTool(miningPos);
        ItemStack toolStack = (bestTool >= 0 && bestTool < 9) ? player.getInventory().getItem(bestTool) : player.getMainHandItem();
        float delta = MiningDamageCalculator.calculateDestroyProgress(player, toolStack, state, miningPos) * speed.get();
        progress += delta;
        if (progress >= breakThreshold.get()) {
            int toolSlot = findBestHotbarTool(miningPos);
            if (toolSlot >= 0 && silentSwitch.get()) {
                InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, 2);
            } else if (toolSlot >= 0 && autoSwitch.get()) {
                InventorySwap.INSTANCE.selectHotbar(toolSlot);
            }

            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    miningPos,
                    miningSide
            ));

            if (swing.get()) {
                player.swing(InteractionHand.MAIN_HAND);
            }

            if (instantRebreak.get()) {
                rebreakPos = miningPos;
                rebreakSide = miningSide;
            }

            miningPos = null;
            progress = 0.0f;
            InventorySwap.INSTANCE.releaseHotbar(this);
        }
    }

    private void resetMining() {
        if (miningPos != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    miningPos,
                    miningSide
            ));
        }
        miningPos = null;
        progress = 0.0f;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;

        BlockPos posToRender = miningPos != null ? miningPos : (instantRebreak.get() ? rebreakPos : null);
        if (posToRender == null) return;

        BlockState state = mc.level.getBlockState(posToRender);
        if (state.isAir()) return;

        AABB fullBox = new AABB(posToRender);
        float p = Math.min(1.0f, Math.max(0.0f, progress));

        // Center-expanding box based on progress
        AABB renderBox = fullBox.deflate((1.0 - p) * 0.5);

        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();

        ExplosionRenderUtil.addFilledBox(renderer, renderBox, fill);
        ExplosionRenderUtil.addOutlineBox(renderer, renderBox, line);
    }

    public int findBestHotbarTool(BlockPos pos) {
        if (mc.player == null || mc.level == null || pos == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        return MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);
    }

    public BlockPos getMiningPos() {
        return miningPos;
    }

    public float getMiningProgress() {
        return progress;
    }

    public boolean isMining() {
        return isEnabled() && (miningPos != null || rebreakPos != null);
    }
}
