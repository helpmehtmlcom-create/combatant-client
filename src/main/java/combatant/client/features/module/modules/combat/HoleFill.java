/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@ModuleInfo(
        id = "holefill",
        displayName = "HoleFill",
        aliases = {"FillHoles", "AntiHole"},
        category = ModuleCategory.COMBAT,
        description = "Fills nearby safe holes around opponents to prevent them from retreating."
)
public final class HoleFill extends Module {

    private static final Set<Block> SAFE_BLOCKS = Set.of(
            Blocks.BEDROCK,
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.ANVIL
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("holeFillRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5, 1.0, 6.0);

    private final NumberValue<Double> enemyRange =
            num("holeFillEnemyRange", "enemy_range", 6.0, 2.0, 12.0);

    private final EnumValue<BlocksMode> blocksMode =
            enumSetting("holeFillBlocksMode", "blocks_mode", BlocksMode.OBSIDIAN, BlocksMode.values());

    private final BooleanValue autoDisable =
            bool("holeFillAutoDisable", "auto_disable", false);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("holeFillRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final NumberValue<Integer> delay =
            num("holeFillDelay", "delay", 0, 0, 10);

    private final BooleanValue render =
            bool("holeFillRender", "render", true);

    private final RGBAColorValue fillColor =
            color("holeFillFillColor", "fill_color", "#285A9C44");

    private final RGBAColorValue lineColor =
            color("holeFillLineColor", "line_color", "#55AAFFFF");

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();

    public HoleFill() {
        this.blockPlacer = new BlockPlacer(
                this,
                this,
                10,
                this::findPlacementSlot,
                range::get,
                () -> 0.0,
                delay::get,
                delay::get,
                () -> 0,
                () -> 0,
                () -> 1,
                () -> false,
                () -> true,
                () -> false,
                rotation::get,
                () -> MovementCorrection.SILENT
        );
    }

    @Override
    public void onEnable() {
        blockPlacer.enable();
        updateHoleTargets();
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        currentTargets.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        blockPlacer.tick();
        updateHoleTargets();

        if (autoDisable.get() && currentTargets.isEmpty()) {
            setEnabled(false);
        }
    }

    private void updateHoleTargets() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            currentTargets.clear();
            return;
        }

        List<Player> opponents = findNearbyOpponents(player);
        if (opponents.isEmpty()) {
            currentTargets.clear();
            blockPlacer.update(List.of());
            return;
        }

        BlockPos playerPos = player.blockPosition();
        int r = (int) Math.ceil(range.get());
        double maxDistSq = range.get() * range.get();
        double enemyMaxDistSq = enemyRange.get() * enemyRange.get();

        List<BlockPos> validHoles = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos candidate = playerPos.offset(x, y, z);
                    if (candidate.equals(playerPos)) {
                        continue;
                    }

                    if (player.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5) > maxDistSq) {
                        continue;
                    }

                    if (!isHole(level, candidate)) {
                        continue;
                    }

                    // Check if close enough to any opponent
                    boolean closeToOpponent = false;
                    for (Player opp : opponents) {
                        if (opp.blockPosition().equals(candidate)) {
                            continue;
                        }
                        if (opp.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5) <= enemyMaxDistSq) {
                            closeToOpponent = true;
                            break;
                        }
                    }

                    if (closeToOpponent) {
                        validHoles.add(candidate);
                    }
                }
            }
        }

        currentTargets.clear();
        currentTargets.addAll(validHoles);
        blockPlacer.update(validHoles);
    }

    private List<Player> findNearbyOpponents(LocalPlayer player) {
        List<Player> opponents = new ArrayList<>();
        if (mc.level == null) return opponents;

        double maxDistSq = enemyRange.get() * enemyRange.get() * 4.0;
        for (Player p : mc.level.players()) {
            if (p.equals(player) || p.isSpectator() || p.isCreative()) continue;
            String name = p.getName().getString();
            if (PlayerRelations.get().getFriends().contains(name)) continue;

            if (player.distanceToSqr(p) <= maxDistSq) {
                opponents.add(p);
            }
        }

        return opponents;
    }

    private boolean isHole(ClientLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).canBeReplaced()
                || !level.getBlockState(pos.above()).canBeReplaced()
                || !level.getBlockState(pos.above(2)).canBeReplaced()) {
            return false;
        }

        if (!isSafe(level, pos.below())) {
            return false;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!isSafe(level, pos.relative(dir))) {
                return false;
            }
        }

        return true;
    }

    private boolean isSafe(ClientLevel level, BlockPos pos) {
        return SAFE_BLOCKS.contains(level.getBlockState(pos).getBlock());
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos targetPos) {
        LocalPlayer player = mc.player;
        if (player == null) return null;

        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && isTargetItem(offhand)) {
            return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isTargetItem(stack)) {
                return new BlockPlacer.PlacementSlot(i, InteractionHand.MAIN_HAND, stack);
            }
        }

        return null;
    }

    private boolean isTargetItem(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return switch (blocksMode.get()) {
                case OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
                case WEBS -> block == Blocks.COBWEB;
                case ANY_SAFE -> SAFE_BLOCKS.contains(block) || block == Blocks.COBWEB;
            };
        }
        return false;
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null || currentTargets.isEmpty()) return;

        int fillArgb = fillColor.getArgb();
        int lineArgb = lineColor.getArgb();

        for (BlockPos pos : currentTargets) {
            AABB box = new AABB(pos);
            addFilledBox(renderer, box, fillArgb);
            addOutlineBox(renderer, box, lineArgb);
        }
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

    public enum BlocksMode {
        OBSIDIAN,
        WEBS,
        ANY_SAFE
    }
}
