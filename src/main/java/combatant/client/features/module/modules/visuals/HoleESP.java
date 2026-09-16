/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.render.engine.uniform.MeshBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "holeesp",
        displayName = "HoleESP",
        aliases = {"holes"},
        category = ModuleCategory.VISUALS,
        description = "Highlights safe obsidian, bedrock, 2x1, and void holes for crystal combat positioning."
)
public final class HoleESP extends Module {

    public enum RenderMode {
        BOX,
        OUTLINE,
        FLAT
    }

    public enum HoleType {
        BEDROCK,
        OBSIDIAN,
        TWO_BY_ONE,
        VOID
    }

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    };

    private static final int RESCAN_TICKS = 8;
    private static final int BASE_FILL_ALPHA = 60;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> range =
            num("range", 10, 2, 32);
    private final EnumValue<RenderMode> mode =
            enumMode("mode", RenderMode.BOX);
    private final RGBAColorValue bedrockColor =
            color("bedrock_color", "#FF00FF51");
    private final RGBAColorValue obsidianColor =
            color("obsidian_color", "#FF7A00FF");
    private final RGBAColorValue voidColor =
            color("void_color", "#FFFF0033");

    private List<Hole> cachedHoles = List.of();
    private BlockPos lastPlayerPos;
    private int lastScanTick;
    private int ticks;

    @Override
    public void onDisable() {
        cachedHoles = List.of();
        lastPlayerPos = null;
        lastScanTick = 0;
        ticks = 0;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            cachedHoles = List.of();
            return;
        }

        ticks++;
        BlockPos currentPos = mc.player.blockPosition();
        if (lastPlayerPos == null || !lastPlayerPos.equals(currentPos) || (ticks - lastScanTick) >= RESCAN_TICKS) {
            cachedHoles = scanHoles(mc.level, mc.player.position());
            lastPlayerPos = currentPos;
            lastScanTick = ticks;
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || renderer == null || mc.level == null || mc.player == null || cachedHoles.isEmpty()) {
            return;
        }

        Renderer3D target = depthRenderer != null ? depthRenderer : renderer;
        float previousLineWidth = RenderState.lineWidth;
        RenderState.lineWidth = 1.5f;
        try {
            RenderMode currentMode = mode.get();
            for (Hole hole : cachedHoles) {
                if (!Renderer3D.Culling.isInFrustum(hole.box())) {
                    continue;
                }
                renderHole(target, hole, currentMode);
            }
        } finally {
            RenderState.lineWidth = previousLineWidth;
        }
    }

    private void renderHole(Renderer3D renderer, Hole hole, RenderMode currentMode) {
        AABB box = hole.box();
        int color = hole.argb();
        int line = colorForDistance(box, color, (color >>> 24) & 0xFF);
        int fillBottom = colorForDistance(box, color, BASE_FILL_ALPHA);
        int fillTop = colorForDistance(box, color, 0);

        switch (currentMode) {
            case BOX -> {
                addVerticalFadeBox(renderer, box, fillBottom, fillTop);
                addFullOutline(renderer, box, line);
            }
            case OUTLINE -> addFullOutline(renderer, box, line);
            case FLAT -> {
                addFlatFloor(renderer, box, fillBottom);
                addBottomOutline(renderer, box, line);
            }
        }
    }

    private List<Hole> scanHoles(ClientLevel level, Vec3 center) {
        int r = range.get();
        int rY = Math.min(r, 6);
        List<Hole> found = new ArrayList<>();
        List<AABB> acceptedBoxes = new ArrayList<>();
        LongOpenHashSet consumed = new LongOpenHashSet();

        int minX = Mth.floor(center.x - r);
        int minY = Math.max(level.getMinY(), Mth.floor(center.y - rY));
        int minZ = Mth.floor(center.z - r);
        int maxX = Mth.floor(center.x + r);
        int maxY = Math.min(level.getMaxY() - 1, Mth.floor(center.y + rY));
        int maxZ = Mth.floor(center.z + r);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            if (Math.abs((x + 0.5) - center.x) > r) continue;
            for (int z = minZ; z <= maxZ; z++) {
                if (Math.abs((z + 0.5) - center.z) > r) continue;
                for (int scanY = minY; scanY <= maxY; scanY++) {
                    if (Math.abs((scanY + 0.5) - center.y) > rY) continue;
                    cursor.set(x, scanY, z);
                    if (!isReplaceable(level.getBlockState(cursor))) continue;

                    long packed = cursor.asLong();
                    if (consumed.contains(packed)) continue;

                    BlockPos pos = cursor.immutable();
                    Hole hole = resolveHole(level, pos);
                    if (hole == null || intersectsAny(hole.box(), acceptedBoxes)) continue;

                    found.add(hole);
                    acceptedBoxes.add(hole.box());
                    for (BlockPos holePos : hole.positions()) {
                        consumed.add(holePos.asLong());
                    }
                }
            }
        }

        return List.copyOf(found);
    }

    private Hole resolveHole(ClientLevel level, BlockPos pos) {
        if (!isReplaceable(level, pos)
                || !isReplaceable(level, pos.above())
                || !isReplaceable(level, pos.above(2))) {
            return null;
        }

        // 1. Check for Void hole
        if (isVoidHole(level, pos)) {
            return new Hole(box(pos, 1, 1), voidColor.getArgb(), HoleType.VOID, List.of(pos));
        }

        // 2. Check 1x1 Bedrock hole
        if (validBedrock(level, pos)) {
            return new Hole(box(pos, 1, 1), bedrockColor.getArgb(), HoleType.BEDROCK, List.of(pos));
        }

        // 3. Check 1x1 Obsidian / Indestructible hole
        if (validIndestructible(level, pos)) {
            return new Hole(box(pos, 1, 1), obsidianColor.getArgb(), HoleType.OBSIDIAN, List.of(pos));
        }

        // 4. Check 2x1 hole
        List<BlockPos> two = twoBlockShape(level, pos);
        if (two != null) {
            if (validBedrockShape(level, two)) {
                return new Hole(box(two), bedrockColor.getArgb(), HoleType.TWO_BY_ONE, two);
            }
            if (validIndestructibleShape(level, two)) {
                return new Hole(box(two), obsidianColor.getArgb(), HoleType.TWO_BY_ONE, two);
            }
        }

        return null;
    }

    private boolean isVoidHole(ClientLevel level, BlockPos pos) {
        // Void hole: surrounded horizontally by safe blocks, but opens directly into the void below
        boolean voidBelow = false;
        if (pos.getY() <= level.getMinY()) {
            voidBelow = true;
        } else {
            boolean allAirBelow = true;
            for (int y = pos.getY() - 1; y >= level.getMinY(); y--) {
                if (!isReplaceable(level, new BlockPos(pos.getX(), y, pos.getZ()))) {
                    allAirBelow = false;
                    break;
                }
            }
            voidBelow = allAirBelow;
        }

        if (!voidBelow) {
            return false;
        }

        for (Direction dir : HORIZONTAL) {
            if (!isSafeBlock(level, pos.relative(dir))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validBedrock(ClientLevel level, BlockPos pos) {
        return isBedrock(level, pos.below())
                && isBedrock(level, pos.east())
                && isBedrock(level, pos.west())
                && isBedrock(level, pos.south())
                && isBedrock(level, pos.north());
    }

    private static boolean validIndestructible(ClientLevel level, BlockPos pos) {
        return isSafeBlock(level, pos.below())
                && isSafeBlock(level, pos.east())
                && isSafeBlock(level, pos.west())
                && isSafeBlock(level, pos.south())
                && isSafeBlock(level, pos.north());
    }

    private static List<BlockPos> twoBlockShape(ClientLevel level, BlockPos pos) {
        for (Direction direction : HORIZONTAL) {
            BlockPos other = pos.relative(direction);
            if (isReplaceable(level, other)
                    && isReplaceable(level, other.above())
                    && isReplaceable(level, other.above(2))) {
                return List.of(pos, other);
            }
        }
        return null;
    }

    private static boolean validBedrockShape(ClientLevel level, List<BlockPos> positions) {
        for (BlockPos check : positions) {
            if (!isBedrock(level, check.below())) {
                return false;
            }
            for (Direction direction : HORIZONTAL) {
                BlockPos surround = check.relative(direction);
                if (!positions.contains(surround) && !isBedrock(level, surround)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean validIndestructibleShape(ClientLevel level, List<BlockPos> positions) {
        boolean hasIndestructible = false;
        for (BlockPos check : positions) {
            BlockPos below = check.below();
            if (isIndestructible(level, below)) {
                hasIndestructible = true;
            } else if (!isBedrock(level, below)) {
                return false;
            }

            for (Direction direction : HORIZONTAL) {
                BlockPos surround = check.relative(direction);
                if (positions.contains(surround)) {
                    continue;
                }
                if (isIndestructible(level, surround)) {
                    hasIndestructible = true;
                } else if (!isBedrock(level, surround)) {
                    return false;
                }
            }
        }
        return hasIndestructible;
    }

    private static boolean isSafeBlock(ClientLevel level, BlockPos pos) {
        return isBedrock(level, pos) || isIndestructible(level, pos);
    }

    private static boolean isBedrock(ClientLevel level, BlockPos pos) {
        return level != null && block(level, pos) == Blocks.BEDROCK;
    }

    private static boolean isIndestructible(ClientLevel level, BlockPos pos) {
        Block block = block(level, pos);
        return block == Blocks.OBSIDIAN
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.CRYING_OBSIDIAN
                || block == Blocks.RESPAWN_ANCHOR;
    }

    private static boolean isReplaceable(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
            return false;
        }
        return isReplaceable(level.getBlockState(pos));
    }

    private static boolean isReplaceable(BlockState state) {
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
    }

    private static Block block(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null || pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) {
            return Blocks.AIR;
        }
        return level.getBlockState(pos).getBlock();
    }

    private int colorForDistance(AABB box, int argb, int alpha) {
        if (box == null || mc.player == null) return argb;
        Vec3 center = box.getCenter();
        double dx = center.x - mc.player.getX();
        double dz = center.z - mc.player.getZ();
        double distSqr = dx * dx + dz * dz;
        if (!Double.isFinite(distSqr) || distSqr < 0.0) return argb;
        double maxSqr = Math.max(1.0, (double) range.get() * range.get());
        float factor = (float) (distSqr / maxSqr);
        factor = 1.0f - easeOutExpo(factor);
        factor = Mth.clamp(factor, 0.0f, 1.0f);

        int baseAlpha = (argb >>> 24) & 0xFF;
        int outAlpha = Mth.clamp((int) (factor * Math.min(alpha, baseAlpha)), 0, 255);
        return (outAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static float easeOutExpo(float x) {
        return x >= 1.0f ? 1.0f : (float) (1.0f - Math.pow(2.0, -10.0f * x));
    }

    private static boolean intersectsAny(AABB box, List<AABB> boxes) {
        for (AABB other : boxes) {
            if (other.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private static AABB box(BlockPos pos, int widthX, int widthZ) {
        return new AABB(
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                pos.getX() + widthX,
                pos.getY() + 1.0,
                pos.getZ() + widthZ
        );
    }

    private static AABB box(List<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        return new AABB(minX, minY, minZ, maxX + 1, minY + 1.0, maxZ + 1);
    }

    private static void addVerticalFadeBox(Renderer3D renderer, AABB box, int bottomArgb, int topArgb) {
        MeshBuilder mesh = renderer.batch(CombatantRenderPipelines.WORLD_COLORED, Renderer3D.DepthMode.MAIN);
        if (mesh == null) {
            return;
        }

        int bottomA = (bottomArgb >>> 24) & 0xFF;
        int bottomR = (bottomArgb >>> 16) & 0xFF;
        int bottomG = (bottomArgb >>> 8) & 0xFF;
        int bottomB = bottomArgb & 0xFF;
        int topA = (topArgb >>> 24) & 0xFF;
        int topR = (topArgb >>> 16) & 0xFF;
        int topG = (topArgb >>> 8) & 0xFF;
        int topB = topArgb & 0xFF;

        addGradientQuad(mesh, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ,
                box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        addGradientQuad(mesh, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        addGradientQuad(mesh, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        addGradientQuad(mesh, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ,
                bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
    }

    private static void addFlatFloor(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.quad(box.minX, box.minY, box.minZ,
                box.maxX, box.minY, box.minZ,
                box.maxX, box.minY, box.maxZ,
                box.minX, box.minY, box.maxZ,
                r, g, b, a);
    }

    private static void addGradientQuad(MeshBuilder mesh,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2,
                                        double x3, double y3, double z3,
                                        double x4, double y4, double z4,
                                        int bottomR, int bottomG, int bottomB, int bottomA,
                                        int topR, int topG, int topB, int topA) {
        mesh.ensureQuadCapacity();
        double minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        double maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        double midY = (minY + maxY) * 0.5;
        int i1 = fadeVertex(mesh, x1, y1, z1, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        int i2 = fadeVertex(mesh, x2, y2, z2, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        int i3 = fadeVertex(mesh, x3, y3, z3, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        int i4 = fadeVertex(mesh, x4, y4, z4, midY, bottomR, bottomG, bottomB, bottomA, topR, topG, topB, topA);
        mesh.quad(i1, i2, i3, i4);
    }

    private static int fadeVertex(MeshBuilder mesh,
                                  double x, double y, double z, double midY,
                                  int bottomR, int bottomG, int bottomB, int bottomA,
                                  int topR, int topG, int topB, int topA) {
        boolean bottom = y <= midY;
        return mesh.vec3(x, y, z)
                .color(
                        bottom ? bottomR : topR,
                        bottom ? bottomG : topG,
                        bottom ? bottomB : topB,
                        bottom ? bottomA : topA
                )
                .next();
    }

    private static void addBottomOutline(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);
    }

    private static void addFullOutline(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        // Bottom
        renderer.line(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, r, g, b, a);

        // Top
        renderer.line(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);

        // Verticals
        renderer.line(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.line(box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        renderer.line(box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
    }

    private record Hole(AABB box, int argb, HoleType type, List<BlockPos> positions) {
    }
}
