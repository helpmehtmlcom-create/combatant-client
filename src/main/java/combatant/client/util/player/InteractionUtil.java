/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Portions derived from ThunderHack Recode, copyright (c) 2023-2024 Pan4ur & 06ED.
 * Upstream: https://github.com/Pan4ur/ThunderHack-Recode
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player;

import combatant.client.mixins.accessors.ClientLevelAccessor;
import combatant.client.util.raycast.RaycastUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public enum InteractionUtil {
    ;

    public static final double VANILLA_BLOCK_REACH = RaycastUtil.VANILLA_BLOCK_REACH;
    public static final double VANILLA_ENTITY_REACH = RaycastUtil.VANILLA_ENTITY_REACH;
    public static final double STRICT_ANTI_CHEAT_REACH = RaycastUtil.STRICT_ANTI_CHEAT_REACH;
    public static final double DEFAULT_REACH = RaycastUtil.DEFAULT_REACH;

    public static Vec3 getEyesPos(Entity entity) {
        return entity.position().add(0, entity.getEyeHeight(entity.getPose()), 0);
    }

    public static float[] calculateAngle(Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return new float[]{0f, 0f};
        return calculateAngle(getEyesPos(mc.player), to);
    }

    public static float[] calculateAngle(Vec3 from, Vec3 to) {
        double difX = to.x - from.x;
        double difY = (to.y - from.y) * -1.0;
        double difZ = to.z - from.z;
        double dist = Mth.sqrt((float) (difX * difX + difZ * difZ));

        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        float pitch = (float) Mth.clamp(Mth.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist))), -90f, 90f);

        return new float[]{yaw, pitch};
    }

    public static void sendSequencedPacket(PredictiveAction packetCreator) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.getConnection() == null) return;
        BlockStatePredictionHandler pending = ((ClientLevelAccessor) mc.level).combatant$getPendingUpdateManager();
        try (BlockStatePredictionHandler pendingUpdateManager = pending.startPredicting()) {
            int id = pendingUpdateManager.currentSequence();
            mc.getConnection().send(packetCreator.predict(id));
        }
    }

    /**
     * Sequenced block interaction helper.
     * If LocalPlayer and GameMode are present, executes gameMode.useItemOn (which handles client prediction,
     * item use logic, and internal sequenced packet sending).
     * Otherwise falls back to sending a sequenced ServerboundUseItemOnPacket.
     *
     * @param player the player performing the interaction
     * @param hand the hand used
     * @param hitResult the target block hit result
     * @return the interaction result
     */
    public static InteractionResult interactBlock(Player player, InteractionHand hand, BlockHitResult hitResult) {
        return interactBlock(player, hand, hitResult, true);
    }

    /**
     * Sequenced block interaction helper with optional client swing.
     */
    public static InteractionResult interactBlock(Player player, InteractionHand hand, BlockHitResult hitResult, boolean swingHand) {
        if (player == null || hand == null || hitResult == null) {
            return InteractionResult.PASS;
        }

        Minecraft mc = Minecraft.getInstance();
        InteractionResult result;

        if (mc.gameMode != null && player instanceof LocalPlayer localPlayer) {
            result = mc.gameMode.useItemOn(localPlayer, hand, hitResult);
        } else {
            sendSequencedPacket(id -> new ServerboundUseItemOnPacket(hand, hitResult, id));
            result = InteractionResult.SUCCESS;
        }

        if (swingHand && (result == null || result.consumesAction() || result == InteractionResult.SUCCESS)) {
            player.swing(hand);
        }

        return result;
    }

    /**
     * Sends a sequenced ServerboundUseItemOnPacket without triggering client-side item logic.
     */
    public static void sendSequencedBlockUse(InteractionHand hand, BlockHitResult hitResult) {
        sendSequencedBlockUse(hand, hitResult, false);
    }

    /**
     * Sends a sequenced ServerboundUseItemOnPacket with optional client-side swing.
     */
    public static void sendSequencedBlockUse(InteractionHand hand, BlockHitResult hitResult, boolean swingHand) {
        if (hand == null || hitResult == null) return;
        sendSequencedPacket(id -> new ServerboundUseItemOnPacket(hand, hitResult, id));
        Minecraft mc = Minecraft.getInstance();
        if (swingHand && mc.player != null) {
            mc.player.swing(hand);
        }
    }

    public static BlockHitResult raycastBlock(Level level, Vec3 from, Vec3 to, BlockPos target) {
        return RaycastUtil.raycastBlock(level, from, to, target);
    }

    public static boolean canSee(Player player, Vec3 point) {
        return RaycastUtil.canSee(player, point);
    }

    public static HitResult getHitResult(Player player, float yaw, float pitch, double reach) {
        return RaycastUtil.getHitResult(player, yaw, pitch, reach);
    }

    public static HitResult getHitResult(Player player, boolean strictAntiCheat) {
        return RaycastUtil.getHitResult(player, strictAntiCheat);
    }
}
