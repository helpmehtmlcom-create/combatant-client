/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

@ModuleInfo(
        id = "holesnap",
        displayName = "HoleSnap",
        aliases = {"SnapHole", "HolePull"},
        category = ModuleCategory.MOVEMENT,
        description = "Pulls and snaps you into the nearest safe bedrock or obsidian hole."
)
public final class HoleSnap extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> range =
            numCommon("holeSnapRange", "range", CommonSettingSchemas.COMBAT_RANGE, 4.5, 1.0, 10.0);

    private final NumberValue<Float> speed =
            num("holeSnapSpeed", "speed", 0.35f, 0.1f, 1.0f);

    private final BooleanValue pullDown =
            bool("holeSnapPullDown", "pull_down", true);

    private final NumberValue<Float> pullDownSpeed =
            visibleWhen(num("holeSnapPullDownSpeed", "pull_down_speed", 0.5f, 0.1f, 2.0f), pullDown::get);

    private final BooleanValue render =
            bool("holeSnapRender", "render", true);

    private final RGBAColorValue fillColor =
            color("holeSnapFillColor", "fill_color", "#285A9C44");

    private final RGBAColorValue lineColor =
            color("holeSnapLineColor", "line_color", "#55AAFFFF");

    private BlockPos targetHole = null;

    @Override
    public void onEnable() {
        targetHole = findClosestHole();
        if (targetHole == null) {
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        targetHole = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            setEnabled(false);
            return;
        }

        if (targetHole == null || !isValidHole(mc.level, targetHole)) {
            targetHole = findClosestHole();
            if (targetHole == null) {
                setEnabled(false);
                return;
            }
        }

        Vec3 targetCenter = new Vec3(targetHole.getX() + 0.5, targetHole.getY(), targetHole.getZ() + 0.5);
        double dx = targetCenter.x - player.getX();
        double dz = targetCenter.z - player.getZ();
        double distHorizontal = Math.hypot(dx, dz);

        // Check if player has arrived inside the hole
        if (distHorizontal < 0.12 && Math.abs(player.getY() - targetCenter.y) < 0.5) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            setEnabled(false);
            return;
        }

        double moveSpeed = Math.min(speed.get(), distHorizontal);
        double mx = (dx / distHorizontal) * moveSpeed;
        double mz = (dz / distHorizontal) * moveSpeed;
        double my = player.getDeltaMovement().y;

        if (pullDown.get() && player.getY() > targetHole.getY()) {
            my = Math.min(my, -pullDownSpeed.get());
        }

        player.setDeltaMovement(mx, my, mz);
    }

    private BlockPos findClosestHole() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return null;

        BlockPos playerPos = player.blockPosition();
        int r = (int) Math.ceil(range.get());
        double maxDistSq = range.get() * range.get();

        BlockPos bestHole = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos candidate = playerPos.offset(x, y, z);
                    double distSq = player.distanceToSqr(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                    if (distSq > maxDistSq || distSq >= bestDistSq) {
                        continue;
                    }

                    if (isValidHole(level, candidate)) {
                        bestHole = candidate;
                        bestDistSq = distSq;
                    }
                }
            }
        }

        return bestHole;
    }

    private boolean isValidHole(ClientLevel level, BlockPos pos) {
        if (!isReplaceable(level, pos)
                || !isReplaceable(level, pos.above())
                || !isReplaceable(level, pos.above(2))) {
            return false;
        }

        if (!isSafeBlock(level, pos.below())) {
            return false;
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!isSafeBlock(level, pos.relative(dir))) {
                return false;
            }
        }

        return true;
    }

    private boolean isSafeBlock(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.OBSIDIAN)
                || state.is(Blocks.CRYING_OBSIDIAN)
                || state.is(Blocks.RESPAWN_ANCHOR)
                || state.is(Blocks.NETHERITE_BLOCK)
                || state.is(Blocks.ENDER_CHEST)
                || state.is(Blocks.ANVIL);
    }

    private boolean isReplaceable(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || targetHole == null) return;

        AABB box = new AABB(targetHole);
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
        renderer.quad(box.minX, box.minY, minZ(box), box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, minZ(box), r, g, b, a);
    }

    private static double minZ(AABB box) {
        return box.minZ;
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
