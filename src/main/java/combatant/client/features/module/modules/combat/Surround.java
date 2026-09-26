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
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.placer.BlockPlacer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
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
        id = "surround",
        displayName = "Surround",
        aliases = {"FeetTrap", "ObsidianFeet", "SelfSurround"},
        category = ModuleCategory.COMBAT,
        description = "Surrounds player feet with blast-resistant blocks to protect against crystal explosions."
)
public final class Surround extends Module {

    private static final Set<Block> BLAST_RESISTANT_BLOCKS = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.RESPAWN_ANCHOR,
            Blocks.NETHERITE_BLOCK,
            Blocks.ENDER_CHEST,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL
    );

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Mode> mode =
            enumSetting("surroundMode", "mode", Mode.FEET, Mode.values());

    private final EnumValue<CenterMode> center =
            enumSetting("surroundCenter", "center", CenterMode.TELEPORT, CenterMode.values());

    private final BooleanValue disableOnJump =
            boolCommon("surroundDisableOnJump", "disable_on_jump", CommonSettingSchemas.PLAYER_FALL_CHECK, true);

    private final BooleanValue disableInAir =
            bool("surroundDisableInAir", "disable_in_air", false);

    private final BooleanValue autoDisable =
            bool("surroundAutoDisable", "auto_disable", false);

    private final EnumValue<BlockPlacer.RotationMode> rotation =
            enumSetting("surroundRotation", "rotation", BlockPlacer.RotationMode.NORMAL, BlockPlacer.RotationMode.values());

    private final EnumValue<MovementCorrection> movementCorrection =
            enumSetting("surroundMovementCorrection", "movement_correction", MovementCorrection.SILENT, MovementCorrection.values());

    private final NumberValue<Double> range =
            numCommon("surroundRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5, 1.0, 6.0);

    private final NumberValue<Double> wallRange =
            numCommon("surroundWallRange", "wall_range", CommonSettingSchemas.COMBAT_WALL_RANGE, 3.0, 0.0, 6.0);

    private final NumberValue<Integer> delay =
            num("surroundDelay", "delay", 0, 0, 10);

    private final BooleanValue render =
            bool("surroundRender", "render", true);

    private final RGBAColorValue fillColor =
            color("surroundFillColor", "fill_color", "#285A9C44");

    private final RGBAColorValue lineColor =
            color("surroundLineColor", "line_color", "#55AAFFFF");

    private final NumberValue<Float> lineWidth =
            numCommon("surroundLineWidth", "line_width", CommonSettingSchemas.LINE_WIDTH, 2.0f, 1.0f, 6.0f);

    private final BlockPlacer blockPlacer;
    private final List<BlockPos> currentTargets = new ArrayList<>();
    private BlockPos startFeetPos = null;

    public Surround() {
        this.blockPlacer = new BlockPlacer(
                this,
                this,
                10,
                this::findPlacementSlot,
                range::get,
                wallRange::get,
                delay::get,
                delay::get,
                () -> 0,
                () -> 0,
                () -> 1,
                () -> false,
                () -> true,
                () -> false,
                rotation::get,
                movementCorrection::get
        );
    }

    @Override
    public void onEnable() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        startFeetPos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        applyCentering(player);
        blockPlacer.enable();
        updateSurroundTargets();
    }

    @Override
    public void onDisable() {
        blockPlacer.disable();
        currentTargets.clear();
        startFeetPos = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        if (disableOnJump.get() && (mc.options.keyJump.isDown() || player.getDeltaMovement().y > 0.15)) {
            setEnabled(false);
            return;
        }

        if (disableInAir.get() && !player.onGround()) {
            setEnabled(false);
            return;
        }

        // Center player if using motion centering
        if (center.get() == CenterMode.MOTION) {
            double targetX = Math.floor(player.getX()) + 0.5;
            double targetZ = Math.floor(player.getZ()) + 0.5;
            double dx = targetX - player.getX();
            double dz = targetZ - player.getZ();
            if (Math.abs(dx) > 0.05 || Math.abs(dz) > 0.05) {
                player.setDeltaMovement(dx * 0.4, player.getDeltaMovement().y, dz * 0.4);
            }
        }

        blockPlacer.tick();
        updateSurroundTargets();

        if (autoDisable.get() && currentTargets.isEmpty()) {
            setEnabled(false);
        }
    }

    private void applyCentering(LocalPlayer player) {
        if (player == null) return;

        double targetX = Math.floor(player.getX()) + 0.5;
        double targetZ = Math.floor(player.getZ()) + 0.5;

        if (center.get() == CenterMode.TELEPORT) {
            player.setPos(targetX, player.getY(), targetZ);
            if (player.connection != null) {
                player.connection.send(new ServerboundMovePlayerPacket.Pos(
                        targetX,
                        player.getY(),
                        targetZ,
                        player.onGround(),
                        player.horizontalCollision
                ));
            }
        } else if (center.get() == CenterMode.MOTION) {
            double dx = targetX - player.getX();
            double dz = targetZ - player.getZ();
            player.setDeltaMovement(dx * 0.5, player.getDeltaMovement().y, dz * 0.5);
        }
    }

    private void updateSurroundTargets() {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            currentTargets.clear();
            return;
        }

        BlockPos feetPos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        List<BlockPos> positions = new ArrayList<>();

        // Feet level surrounding blocks
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos sidePos = feetPos.relative(dir);
            positions.add(sidePos);

            // Full mode: place bottom support under each surround block if air
            if (mode.get() == Mode.FULL) {
                BlockPos underSide = sidePos.below();
                if (mc.level.getBlockState(underSide).canBeReplaced()) {
                    positions.add(underSide);
                }
            }

            // Anti-Faceplace: place blocks 1 level higher around player
            if (mode.get() == Mode.ANTI_FACE_PLACE) {
                positions.add(sidePos.above());
            }
        }

        // Full mode: also ensure under player feet is solid
        if (mode.get() == Mode.FULL) {
            BlockPos underFeet = feetPos.below();
            if (mc.level.getBlockState(underFeet).canBeReplaced()) {
                positions.add(underFeet);
            }
        }

        // Filter to positions that are currently replaceable
        List<BlockPos> needsPlacement = new ArrayList<>();
        for (BlockPos pos : positions) {
            BlockState state = mc.level.getBlockState(pos);
            if (state.canBeReplaced()) {
                needsPlacement.add(pos);
            }
        }

        currentTargets.clear();
        currentTargets.addAll(needsPlacement);
        blockPlacer.update(needsPlacement);
    }

    private BlockPlacer.PlacementSlot findPlacementSlot(BlockPos targetPos) {
        LocalPlayer player = mc.player;
        if (player == null) return null;

        // Check offhand first
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && isBlastResistant(offhand)) {
            return new BlockPlacer.PlacementSlot(-1, InteractionHand.OFF_HAND, offhand);
        }

        // Check hotbar (slots 0..8)
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && isBlastResistant(stack)) {
                return new BlockPlacer.PlacementSlot(i, InteractionHand.MAIN_HAND, stack);
            }
        }

        return null;
    }

    private boolean isBlastResistant(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            return BLAST_RESISTANT_BLOCKS.contains(blockItem.getBlock());
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

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.quad(minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a);
        renderer.quad(minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.quad(maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a);
        renderer.quad(minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
    }

    private static void addOutlineBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;

        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
    }

    public enum Mode {
        FEET,
        FULL,
        ANTI_FACE_PLACE
    }

    public enum CenterMode {
        NONE,
        TELEPORT,
        MOTION
    }
}
