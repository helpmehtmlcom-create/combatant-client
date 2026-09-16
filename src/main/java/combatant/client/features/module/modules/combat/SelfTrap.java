/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.block.placer.BlockPlacer;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Automatically places blast-resistant blocks above player's head and upper sides
 * to prevent cev breaker, crystal attacks, and anvil/anchor placements.
 */
@ModuleInfo(
        id = "selftrap",
        displayName = "SelfTrap",
        aliases = {"AutoSelfTrap", "TrapSelf"},
        category = ModuleCategory.COMBAT,
        description = "Places blast-resistant blocks above your head and sides to protect against crystal attacks."
)
public class SelfTrap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    public enum SelfTrapMode {
        FULL,
        HEAD
    }

    public enum TrapBlock {
        OBSIDIAN,
        ENDER_CHEST,
        ANCHOR
    }

    private final EnumValue<SelfTrapMode> mode =
            enumSetting("selftrap_mode", "mode", SelfTrapMode.FULL, SelfTrapMode.values());
    private final NumberValue<Integer> blocksPerTick =
            num("selftrap_blocks_per_tick", "blocks_per_tick", 4, 1, 8);
    private final EnumValue<TrapBlock> blockType =
            enumSetting("selftrap_block", "block", TrapBlock.OBSIDIAN, TrapBlock.values());
    private final BooleanValue rotate =
            bool("selftrap_rotate", "rotate", true);

    @Override
    public void onDisable() {
        RotationManager.INSTANCE.clear(this);
    }

    @EventHandler(priority = 25)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.gameMode == null) {
            return;
        }

        tickSelfTrap(player, level);
    }

    private void tickSelfTrap(LocalPlayer player, Level level) {
        if (!player.isAlive() || player.isSpectator()) return;

        List<BlockPos> requiredPositions = getRequiredTrapPositions(player, level);
        if (requiredPositions.isEmpty()) {
            return;
        }

        // Prioritize bottom-to-top placement order so blocks always have valid attachable faces
        requiredPositions.sort(Comparator.comparingInt((BlockPos p) -> p.getY())
                .thenComparingDouble(p -> player.getEyePosition().distanceToSqr(Vec3.atCenterOf(p))));

        int maxBlocks = blocksPerTick.get();
        int placedCount = 0;
        Set<BlockPos> placedThisTick = new HashSet<>();

        for (BlockPos pos : requiredPositions) {
            if (placedCount >= maxBlocks) break;
            if (!isReplaceable(level, pos, placedThisTick) || isEntityBlocked(player, level, pos)) {
                continue;
            }

            BlockHitResult hit = BlockPlacer.findOptimalPlacementHit(level, player, pos, 5.0);

            // If direct face not found, check support block directly below to anchor placement
            if (hit == null) {
                BlockPos support = pos.below();
                if (isReplaceable(level, support, placedThisTick) && !isEntityBlocked(player, level, support)) {
                    BlockHitResult supportHit = BlockPlacer.findOptimalPlacementHit(level, player, support, 5.0);
                    if (supportHit != null) {
                        if (placeBlockAt(player, supportHit)) {
                            placedThisTick.add(support);
                            placedCount++;
                            if (placedCount >= maxBlocks) break;
                            hit = BlockPlacer.findOptimalPlacementHit(level, player, pos, 5.0);
                        }
                    }
                }
            }

            if (hit != null) {
                if (placeBlockAt(player, hit)) {
                    placedThisTick.add(pos);
                    placedCount++;
                }
            }
        }
    }

    private boolean placeBlockAt(LocalPlayer player, BlockHitResult hit) {
        if (hit == null) return false;

        InteractionHand hand = null;
        int slot = -1;
        TrapBlock blockChoice = blockType.get();

        if (isTargetBlock(player.getOffhandItem(), blockChoice)) {
            hand = InteractionHand.OFF_HAND;
        } else {
            slot = findBlockSlot(player, blockChoice);
            if (slot != -1) {
                hand = InteractionHand.MAIN_HAND;
            }
        }

        if (hand == null) return false;

        return BlockPlacer.placeBlock(
                this,
                hit,
                hand,
                slot,
                rotate.get(),
                BlockPlacer.SwingMode.CLIENT_AND_SERVER
        );
    }

    private List<BlockPos> getRequiredTrapPositions(LocalPlayer player, Level level) {
        List<BlockPos> basePositions = resolveBasePositions(player);
        List<BlockPos> required = new ArrayList<>();
        SelfTrapMode m = mode.get();

        for (BlockPos base : basePositions) {
            // Level 0: Surround feet (NORTH, EAST, SOUTH, WEST) for full-body enclosing
            if (m == SelfTrapMode.FULL) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos surround = base.relative(dir);
                    if (!required.contains(surround)) {
                        required.add(surround);
                    }
                }
            }

            // Level 1: Upper sides (surrounding head/eye level)
            BlockPos upper = base.above();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos upperSide = upper.relative(dir);
                if (!required.contains(upperSide)) {
                    required.add(upperSide);
                }
            }

            // Level 2: Top / Head roof directly above player
            BlockPos headPos = base.above(2);
            if (!required.contains(headPos)) {
                required.add(headPos);
            }
        }

        return required;
    }

    private List<BlockPos> resolveBasePositions(LocalPlayer player) {
        AABB box = player.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ);
        int y = Mth.floor(player.getY() + 0.1);

        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!positions.contains(pos)) {
                    positions.add(pos);
                }
            }
        }
        return positions.isEmpty() ? List.of(player.blockPosition()) : positions;
    }

    private boolean isReplaceable(Level level, BlockPos pos, Set<BlockPos> placedThisTick) {
        if (placedThisTick != null && placedThisTick.contains(pos)) {
            return false;
        }
        if (!level.isInWorldBounds(pos)) return false;
        return level.getBlockState(pos).canBeReplaced();
    }

    private boolean isEntityBlocked(LocalPlayer player, Level level, BlockPos pos) {
        AABB box = new AABB(pos);
        if (player.getBoundingBox().intersects(box)) {
            return true;
        }
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box);
        for (Entity entity : entities) {
            if (entity.isAlive()) return true;
        }
        return false;
    }

    private boolean isTargetBlock(ItemStack stack, TrapBlock type) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return switch (type) {
            case OBSIDIAN -> block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN;
            case ENDER_CHEST -> block == Blocks.ENDER_CHEST;
            case ANCHOR -> block == Blocks.RESPAWN_ANCHOR;
        };
    }

    private int findBlockSlot(LocalPlayer player, TrapBlock type) {
        int selected = InventorySwap.INSTANCE.clientSelectedSlot();
        if (selected >= 0 && selected < 9) {
            ItemStack held = player.getInventory().getItem(selected);
            if (isTargetBlock(held, type)) {
                return selected;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isTargetBlock(stack, type)) {
                return slot;
            }
        }

        return -1;
    }
}
