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
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "cevbreaker",
        displayName = "CevBreaker",
        aliases = {"AutoCev", "CevAttack"},
        category = ModuleCategory.COMBAT,
        description = "Automates the CEV ceiling crystal placement, mining, and detonation sequence."
)
public final class CevBreaker extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("cevBreakerRange", "range", CommonSettingSchemas.COMBAT_RANGE, 5.0, 2.0, 7.0);

    private final NumberValue<Double> targetRange =
            num("cevBreakerTargetRange", "target_range", 6.0, 2.0, 10.0);

    private final BooleanValue autoDisable =
            bool("cevBreakerAutoDisable", "auto_disable", false);

    private final BooleanValue silentTool =
            bool("cevBreakerSilentTool", "silent_tool", true);

    private final BooleanValue render =
            bool("cevBreakerRender", "render", true);

    private final RGBAColorValue fillColor =
            color("cevBreakerFillColor", "fill_color", "#FF005544");

    private final RGBAColorValue lineColor =
            color("cevBreakerLineColor", "line_color", "#FF3300FF");

    private BlockPos ceilingPos = null;
    private float breakProgress = 0.0f;
    private boolean isMining = false;

    @Override
    public void onDisable() {
        if (isMining && ceilingPos != null && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    ceilingPos,
                    Direction.UP
            ));
        }
        ceilingPos = null;
        breakProgress = 0.0f;
        isMining = false;
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || mc.getConnection() == null) {
            ceilingPos = null;
            return;
        }

        Player opponent = findTargetOpponent(player);
        if (opponent == null) {
            ceilingPos = null;
            breakProgress = 0.0f;
            isMining = false;
            return;
        }

        ceilingPos = opponent.blockPosition().above(2);
        double maxDistSq = range.get() * range.get();
        if (player.distanceToSqr(ceilingPos.getX() + 0.5, ceilingPos.getY() + 0.5, ceilingPos.getZ() + 0.5) > maxDistSq) {
            ceilingPos = null;
            return;
        }

        BlockState state = level.getBlockState(ceilingPos);

        // 1. Place Obsidian ceiling if replaceable
        if (state.canBeReplaced()) {
            int obbySlot = findItemSlot(player, Items.OBSIDIAN);
            if (obbySlot != -1) {
                InventorySwap.INSTANCE.leaseHotbar(this, obbySlot, 2);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(ceilingPos),
                        Direction.UP,
                        ceilingPos.below(),
                        false
                );
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                player.swing(InteractionHand.MAIN_HAND);
            }
            breakProgress = 0.0f;
            isMining = false;
            return;
        }

        // 2. Place End Crystal on top of Obsidian if crystal not present
        BlockPos crystalPos = ceilingPos.above();
        EndCrystal existingCrystal = findCrystalAt(level, crystalPos);

        if (existingCrystal == null && level.getBlockState(crystalPos).canBeReplaced()) {
            int crystalSlot = findItemSlot(player, Items.END_CRYSTAL);
            if (crystalSlot != -1) {
                InventorySwap.INSTANCE.leaseHotbar(this, crystalSlot, 2);
                BlockHitResult hit = new BlockHitResult(
                        Vec3.atCenterOf(crystalPos),
                        Direction.UP,
                        ceilingPos,
                        false
                );
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
                player.swing(InteractionHand.MAIN_HAND);
            }
        }

        // 3. Packet mine the ceiling Obsidian
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)) {
            if (!isMining) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                        ceilingPos,
                        Direction.UP
                ));
                isMining = true;
            }

            float delta = state.getDestroyProgress(player, level, ceilingPos);
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
                        ceilingPos,
                        Direction.UP
                ));
                player.swing(InteractionHand.MAIN_HAND);

                // 4. Detonate crystal immediately upon ceiling break!
                EndCrystal toDetonate = findCrystalAt(level, crystalPos);
                if (toDetonate != null) {
                    mc.gameMode.attack(player, toDetonate);
                    player.swing(InteractionHand.MAIN_HAND);
                }

                breakProgress = 0.0f;
                isMining = false;

                if (autoDisable.get()) {
                    setEnabled(false);
                }
            }
        }
    }

    private EndCrystal findCrystalAt(ClientLevel level, BlockPos pos) {
        AABB box = new AABB(pos).inflate(1.0);
        for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, box, c -> c != null && c.isAlive())) {
            return crystal;
        }
        return null;
    }

    private Player findTargetOpponent(LocalPlayer player) {
        if (mc.level == null) return null;

        Player closest = null;
        double closestDistSq = targetRange.get() * targetRange.get();

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

    private int findItemSlot(LocalPlayer player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                return i;
            }
        }
        return -1;
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
        if (!isEnabled() || !render.get() || ceilingPos == null) return;

        AABB box = new AABB(ceilingPos);
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
