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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
import combatant.client.util.block.mining.BlockMiningSystem;
import combatant.client.util.block.mining.MiningDamageCalculator;
import combatant.client.util.block.mining.MiningTask;
import combatant.client.util.combat.ExplosionRenderUtil;
import combatant.client.util.player.inventory.InventorySwap;

@ModuleInfo(
        id = "speedmine",
        displayName = "SpeedMine",
        category = ModuleCategory.PLAYER,
        description = "Accelerates block breaking with custom packet speeds, instant rebreaking, and damage modifiers."
)
public final class SpeedMine extends Module {

    public enum Mode implements EnumValue.IdProvider {
        PACKET("packet"),
        DAMAGE("damage"),
        INSTANT("instant");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode = tooltip(
            enumSetting("speedmineMode", "mode", Mode.PACKET, Mode.values()),
            "Mining mode: Packet for custom speed packets, Damage for client-side multiplier, Instant for immediate break."
    );

    private final NumberValue<Float> speed = visibleWhen(
            tooltip(num("speedmineSpeed", "speed", 1.0f, 0.1f, 3.0f), "Speed multiplier applied to block destroy progress."),
            () -> mode.get() != Mode.INSTANT
    );

    private final NumberValue<Float> breakThreshold = visibleWhen(
            tooltip(num("speedmineBreakThreshold", "threshold", 1.0f, 0.7f, 1.0f), "Progress threshold required before sending final break packet."),
            () -> mode.get() == Mode.PACKET
    );

    private final NumberValue<Float> startProgress = visibleWhen(
            tooltip(num("speedmineStartProgress", "start_progress", 0.0f, 0.0f, 0.9f), "Initial progress offset applied when starting to mine a block."),
            () -> mode.get() == Mode.PACKET
    );

    private final NumberValue<Float> range = tooltip(
            num("speedmineRange", "range", 5.5f, 2.0f, 7.0f),
            "Maximum reach distance from eye position to mine blocks."
    );

    private final BooleanValue instantRebreak = tooltip(
            bool("speedmineInstantRebreak", "instant_rebreak", true),
            "Instantly rebreaks previously mined blocks when a new block is placed there (CivBreak)."
    );

    private final BooleanValue silentSwitch = tooltip(
            bool("speedmineSilentSwitch", "silent_switch", true),
            "Silently leases the best tool packet-side when finishing or rebreaking without changing selected hotbar slot."
    );

    private final BooleanValue autoSwitch = visibleWhen(
            tooltip(bool("speedmineAutoSwitch", "auto_switch", false), "Automatically selects the best hotbar tool while mining."),
            () -> !silentSwitch.get()
    );

    private final BooleanValue silentRotate = tooltip(
            bool("speedmineSilentRotate", "silent_rotate", false),
            "Silently snaps server-side rotation to the mined block face."
    );

    private final BooleanValue resetDelay = tooltip(
            bool("speedmineResetDelay", "reset_delay", true),
            "Removes the vanilla 5-tick block breaking reset delay."
    );

    private final BooleanValue swing = tooltip(
            bool("speedmineSwing", "swing", true),
            "Swings hand when sending mining action packets."
    );

    private final BooleanValue render = tooltip(
            bool("speedmineRender", "render", true),
            "Renders a progressive 3D bounding box overlay on blocks currently being mined."
    );

    private final RGBAColorValue fillColor = visibleWhen(
            color("speedmineFillColor", "fill_color", "#285A9C55"),
            render::get
    );

    private final RGBAColorValue lineColor = visibleWhen(
            color("speedmineLineColor", "line_color", "#FF9BE4FF"),
            render::get
    );

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.AFTER_POST_PROCESS;
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void reset() {
        BlockMiningSystem.INSTANCE.reset();
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
            BlockMiningSystem.INSTANCE.instantBreak(pos, side, silentSwitch.get(), autoSwitch.get(), silentRotate.get(), swing.get(), this);
            return true;
        }

        if (mode.get() == Mode.PACKET) {
            MiningTask current = BlockMiningSystem.INSTANCE.getPrimaryTask();
            if (current != null && pos.equals(current.getPos())) {
                return true;
            }
            startMining(pos, side);
            return true;
        }

        return false;
    }

    public void startMining(BlockPos pos, Direction side) {
        if (pos == null || mc.player == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0) return;

        BlockMiningSystem.INSTANCE.startMining(pos, side, true, speed.get(), startProgress.get());
        if (swing.get() && mc.player != null) {
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    public void startSecondaryMining(BlockPos pos, Direction side) {
        if (pos == null || mc.player == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0) return;

        BlockMiningSystem.INSTANCE.startMining(pos, side, false, speed.get(), startProgress.get());
        if (swing.get() && mc.player != null) {
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    public void abortMining(boolean isPrimary) {
        BlockMiningSystem.INSTANCE.abortMining(isPrimary);
    }

    public void abortAllMining() {
        BlockMiningSystem.INSTANCE.abortAllMining();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || mc.player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }

        LocalPlayer player = mc.player;

        // Check instant rebreak target if enabled
        if (instantRebreak.get()) {
            BlockMiningSystem.INSTANCE.handleInstantRebreak(
                    player,
                    range.get(),
                    silentSwitch.get(),
                    autoSwitch.get(),
                    silentRotate.get(),
                    swing.get(),
                    this
            );
        }

        // Tick primary and secondary tasks through BlockMiningSystem
        BlockMiningSystem.INSTANCE.tick(
                player,
                silentSwitch.get(),
                autoSwitch.get(),
                silentRotate.get(),
                swing.get(),
                breakThreshold.get(),
                range.get(),
                this
        );
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;

        int fill = fillColor.getArgb();
        int line = lineColor.getArgb();

        // Render primary mining block or instant rebreak target
        MiningTask primary = BlockMiningSystem.INSTANCE.getPrimaryTask();
        if (primary != null) {
            renderMiningBox(renderer, primary.getPos(), primary.getProgress(), fill, line);
        } else if (instantRebreak.get()) {
            BlockPos rebreakPos = BlockMiningSystem.INSTANCE.getRebreakPos();
            if (rebreakPos != null) {
                BlockState state = mc.level.getBlockState(rebreakPos);
                if (!state.isAir() && state.getBlock() != Blocks.BEDROCK) {
                    renderMiningBox(renderer, rebreakPos, 1.0f, fill, line);
                }
            }
        }

        // Render secondary mining block (double mine)
        MiningTask secondary = BlockMiningSystem.INSTANCE.getSecondaryTask();
        if (secondary != null) {
            renderMiningBox(renderer, secondary.getPos(), secondary.getProgress(), fill, line);
        }
    }

    private void renderMiningBox(Renderer3D renderer, BlockPos pos, float currentProgress, int fill, int line) {
        if (pos == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        AABB fullBox = new AABB(pos);
        float p = Math.min(1.0f, Math.max(0.0f, currentProgress));
        AABB renderBox = fullBox.deflate((1.0 - p) * 0.5);

        ExplosionRenderUtil.addFilledBox(renderer, renderBox, fill);
        ExplosionRenderUtil.addOutlineBox(renderer, renderBox, line);
    }

    public int findBestHotbarTool(BlockPos pos) {
        if (mc.player == null || mc.level == null || pos == null) return -1;
        BlockState state = mc.level.getBlockState(pos);
        return MiningDamageCalculator.findBestHotbarTool(mc.player, state, pos);
    }

    public BlockPos getMiningPos() {
        MiningTask task = BlockMiningSystem.INSTANCE.getPrimaryTask();
        return task != null ? task.getPos() : BlockMiningSystem.INSTANCE.getRebreakPos();
    }

    public float getMiningProgress() {
        MiningTask task = BlockMiningSystem.INSTANCE.getPrimaryTask();
        return task != null ? task.getProgress() : 0.0f;
    }

    public BlockPos getSecondaryMiningPos() {
        MiningTask task = BlockMiningSystem.INSTANCE.getSecondaryTask();
        return task != null ? task.getPos() : null;
    }

    public float getSecondaryMiningProgress() {
        MiningTask task = BlockMiningSystem.INSTANCE.getSecondaryTask();
        return task != null ? task.getProgress() : 0.0f;
    }

    public boolean isMining() {
        return isEnabled() && BlockMiningSystem.INSTANCE.isMining();
    }

    public boolean isMining(BlockPos pos) {
        return isEnabled() && BlockMiningSystem.INSTANCE.isMining(pos);
    }

    public float getSpeed() {
        return speed.get();
    }

    public float getRange() {
        return range.get();
    }

    public boolean isInstantRebreak() {
        return instantRebreak.get();
    }

    public boolean isSilentSwitch() {
        return silentSwitch.get();
    }

    public boolean isAutoSwitch() {
        return autoSwitch.get();
    }

    public boolean isSilentRotate() {
        return silentRotate.get();
    }

    public boolean isSwing() {
        return swing.get();
    }

    public float getBreakThreshold() {
        return breakThreshold.get();
    }
}
