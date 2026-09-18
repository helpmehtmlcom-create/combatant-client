/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import combatant.client.mixins.accessors.PlayerInventoryAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-salvages spawners when a stranger approaches or breaks blocks nearby,
 * deposits them into an Ender Chest, and safely logs out on DonutSMP.
 * Ported and adapted from 67Client's SpawnerProtectModule.
 */
@ModuleInfo(
        id = "spawnerprotect",
        displayName = "SpawnerProtect",
        description = "Auto-salvages your spawners when a stranger approaches or breaks blocks, deposits to Ender Chest, then logs out.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"spawnersave", "autosalvage", "baseshield"}
)
public class SpawnerProtect extends Module {

    private final NumberValue<Double> scanRange =
            num("spawnerprotect_scan_range", "scan_range", 64.0, 16.0, 128.0);
    private final NumberValue<Double> breakRange =
            num("spawnerprotect_break_range", "break_range", 5.5, 2.0, 7.0);
    private final BooleanValue detectPackets =
            bool("spawnerprotect_detect_packets", "detect_packets", true);
    private final BooleanValue autoDeposit =
            bool("spawnerprotect_deposit", "auto_deposit", true);
    private final BooleanValue autoDisconnect =
            bool("spawnerprotect_disconnect", "auto_disconnect", true);
    private final BooleanValue chatAlert =
            bool("spawnerprotect_chat_alert", "chat_alert", true);

    private final Minecraft mc = Minecraft.getInstance();

    private State state = State.IDLE;
    private BlockPos currentTarget = null;
    private int breakProgressTicks = 0;
    private int postActionDelay = 0;

    @Override
    public void onEnable() {
        state = State.IDLE;
        currentTarget = null;
        breakProgressTicks = 0;
        postActionDelay = 0;
    }

    @Override
    public void onDisable() {
        state = State.IDLE;
        currentTarget = null;
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!detectPackets.get() || state != State.IDLE || mc.player == null || mc.level == null) return;

        if (event.getPacket() instanceof ClientboundBlockDestructionPacket packet) {
            BlockPos pos = packet.getPos();
            double distSq = pos.distToCenterSqr(mc.player.position());
            double maxDist = scanRange.get();

            if (distSq <= maxDist * maxDist && distSq > 9.0) {
                warn("Remote block break packet detected at " + pos.toShortString() + "! Triggering spawner salvage routine.");
                state = State.MINING;
            }
        }
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null) return;

        // 1. Scan for strangers and thrown pearls when IDLE
        if (state == State.IDLE) {
            double rangeSq = scanRange.get() * scanRange.get();

            // Check players
            for (Player other : mc.level.players()) {
                if (other == mc.player || other.isSpectator()) continue;
                if (other.distanceToSqr(mc.player) <= rangeSq) {
                    warn("Stranger detected: " + other.getName().getString() + "! Triggering spawner salvage routine.");
                    state = State.MINING;
                    break;
                }
            }

            // Check pearls
            if (state == State.IDLE) {
                for (Entity entity : mc.level.entitiesForRendering()) {
                    if (entity.getType() == EntityTypes.ENDER_PEARL) {
                        if (entity.distanceToSqr(mc.player) <= rangeSq) {
                            warn("Incoming Ender Pearl spotted! Triggering spawner salvage routine.");
                            state = State.MINING;
                            break;
                        }
                    }
                }
            }
        }

        // 2. State Machine
        switch (state) {
            case MINING -> handleMining();
            case DEPOSITING -> handleDepositing();
            case FINISHED -> handleFinished();
            default -> {}
        }
    }

    private void handleMining() {
        List<BlockPos> spawners = findNearbySpawners();
        if (spawners.isEmpty()) {
            // All spawners broken
            if (autoDeposit.get() && hasSpawnersInInventory()) {
                state = State.DEPOSITING;
            } else {
                state = State.FINISHED;
            }
            return;
        }

        BlockPos target = spawners.get(0);
        currentTarget = target;

        // Equip best pickaxe
        equipBestPickaxe();

        // Break block
        if (mc.gameMode != null) {
            mc.gameMode.startDestroyBlock(target, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
            breakProgressTicks++;

            if (breakProgressTicks > 40 || mc.level.getBlockState(target).isAir()) {
                mc.gameMode.stopDestroyBlock();
                breakProgressTicks = 0;
            }
        }
    }

    private void handleDepositing() {
        BlockPos echest = findNearestEnderChest();
        if (echest == null) {
            // No ender chest found, finish immediately
            state = State.FINISHED;
            return;
        }

        if (postActionDelay++ < 10) return;

        // Interact with Ender Chest
        if (mc.gameMode != null) {
            Vec3 hitVec = Vec3.atCenterOf(echest);
            BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, echest, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            state = State.FINISHED;
        }
    }

    private void handleFinished() {
        warn("SpawnerProtect routine completed. Securing assets.");
        if (autoDisconnect.get() && mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            mc.getConnection().getConnection().disconnect(
                    Component.literal("[SpawnerProtect] Spawners secured. Auto-disconnected safely.")
            );
        }
        setEnabled(false);
    }

    private List<BlockPos> findNearbySpawners() {
        List<BlockPos> list = new ArrayList<>();
        if (mc.player == null || mc.level == null) return list;

        int r = (int) Math.ceil(breakRange.get());
        BlockPos p = mc.player.blockPosition();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos bp = p.offset(x, y, z);
                    if (mc.level.getBlockState(bp).is(Blocks.SPAWNER)) {
                        if (bp.distToCenterSqr(mc.player.position()) <= breakRange.get() * breakRange.get()) {
                            list.add(bp);
                        }
                    }
                }
            }
        }
        return list;
    }

    private BlockPos findNearestEnderChest() {
        if (mc.player == null || mc.level == null) return null;
        int r = 5;
        BlockPos p = mc.player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos bp = p.offset(x, y, z);
                    if (mc.level.getBlockState(bp).is(Blocks.ENDER_CHEST)) {
                        double d = bp.distToCenterSqr(mc.player.position());
                        if (d < bestDist) {
                            bestDist = d;
                            best = bp;
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean hasSpawnersInInventory() {
        if (mc.player == null) return false;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.SPAWNER)) {
                return true;
            }
        }
        return false;
    }

    private void equipBestPickaxe() {
        if (mc.player == null) return;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.is(Items.NETHERITE_PICKAXE) || stack.is(Items.DIAMOND_PICKAXE))) {
                ((PlayerInventoryAccessor) mc.player.getInventory()).combatant$setSelectedSlot(i);
                return;
            }
        }
    }

    private void warn(String msg) {
        if (chatAlert.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("§c[SpawnerProtect] §e" + msg)
            );
        }
    }

    private enum State {
        IDLE,
        MINING,
        DEPOSITING,
        FINISHED
    }
}
