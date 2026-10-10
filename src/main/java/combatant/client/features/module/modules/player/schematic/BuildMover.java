/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.events.impl.MovementInputEvent;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.modules.movement.holesnap.Steering;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.schematic.walk.PillarPlanner;
import combatant.client.util.schematic.walk.ReachModel;
import combatant.client.util.schematic.walk.StandingSpotChooser;
import combatant.client.util.schematic.walk.WalkPathfinder;
import combatant.client.util.schematic.walk.WalkTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Moves the player for {@link SchematicBuilder} with nothing but the movement keys: walks to a spot from which
 * the next missing cells can be clicked, jump-places a support column when no spot reaches them, and breaks those
 * support blocks again once the build is done.
 *
 * <p>Everything the server sees is ordinary input: keys pressed through {@link MovementInputEvent}, one jump at a
 * time from the ground, no sprint, no velocity writes. Placement and mining clicks go out from the builder's
 * pre-tick hook, before the movement packet, the same order vanilla uses.</p>
 */
final class BuildMover {

    enum Mode {
        IDLE,
        WALKING,
        PILLARING,
        CLEARING
    }

    /** What the builder lends the mover. */
    interface Host {
        /** Missing cells of the working layers that the inventory has material for, packed; returns the count. */
        int walkTargets(LocalPlayer player, long[] out);

        double reach();

        boolean inFootprint(long cell);

        void aim(Rotation rotation);

        int stuckTimeout();

        boolean pillarEnabled();

        Set<String> helperIds();

        /** Ends the builder's silent hotbar lease so a tool switch is what the server sees. */
        void releaseHotbar();
    }

    /** A support block click the builder should send this tick. */
    record HelperClick(BuildPlanner.Plan plan, int hotbarSlot, BlockState expected) {
    }

    private static final int MAX_TARGETS = 1024;
    private static final double FLOOD_COST = 72.0;
    private static final int FLOOD_NODES = 8000;
    private static final int PATH_NODES = 8000;
    private static final int MAX_DROP = 1;
    /** Getting off a finished build to break the support blocks may need a slightly bigger, still harmless drop. */
    private static final int CLEAR_MAX_DROP = 2;
    private static final int MAX_PILLAR = 8;
    /** Walk planning works with a slightly shorter reach than clicks do: the player never stops dead centre. */
    private static final double PLAN_MARGIN = 0.35;
    private static final double ARRIVE_DISTANCE = 0.3;
    private static final double PASS_DISTANCE = 0.45;
    private static final double CENTER_DISTANCE = 0.2;
    private static final int STALL_REPLAN_TICKS = 20;
    private static final int REPLAN_WAIT_TICKS = 40;
    private static final int ABORT_COOLDOWN_TICKS = 100;
    private static final int ARRIVAL_GRACE_TICKS = 3;
    private static final int MAX_PILLAR_RETRIES = 4;
    private static final float PILLAR_PITCH_READY = 80.0f;

    private final Minecraft mc;
    private final Host host;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final long[] targets = new long[MAX_TARGETS];
    private final Set<Long> rejected = new HashSet<>();
    private final Map<Long, Block> helpers = new LinkedHashMap<>();

    private Mode mode = Mode.IDLE;
    private List<Long> route;
    private int routeIndex;
    private long goal;
    private boolean hasGoal;
    private StandingSpotChooser.Spot spot;
    private PillarPlanner.Pillar pillar;
    private int pillarBaseY = Integer.MIN_VALUE;
    private int pillarRetries;
    private boolean jumpThisTick;
    private boolean controlling;
    private boolean holdStill;
    private double bestWaypointDistance;
    private int stallTicks;
    private int noProgressTicks;
    private int replanWait;
    private int abortCooldown;
    private int arrivalGrace;
    private boolean warnedUnreachable;
    private BlockPos mining;
    private Direction miningFace;
    private Vec3 lastPosition;

    private int routes;
    private int routeCells;
    private double walked;
    private int pillarBlocks;
    private int helpersRemoved;
    private int aborts;

    BuildMover(Minecraft mc, Host host) {
        this.mc = mc;
        this.host = host;
    }

    Mode mode() {
        return mode;
    }

    boolean hasHelpers() {
        return !helpers.isEmpty();
    }

    int routes() {
        return routes;
    }

    int routeCells() {
        return routeCells;
    }

    double walked() {
        return walked;
    }

    int pillarBlocks() {
        return pillarBlocks;
    }

    int helpersRemoved() {
        return helpersRemoved;
    }

    int aborts() {
        return aborts;
    }

    /** Forget the route and stop driving the keys; support blocks already placed stay tracked. */
    void reset() {
        stopMining();
        mode = Mode.IDLE;
        route = null;
        hasGoal = false;
        spot = null;
        pillar = null;
        pillarBaseY = Integer.MIN_VALUE;
        controlling = false;
        holdStill = false;
        jumpThisTick = false;
        replanWait = 0;
        abortCooldown = 0;
        rejected.clear();
        warnedUnreachable = false;
    }

    /** Module switched off: everything goes, the counters too. */
    void clearAll() {
        reset();
        helpers.clear();
        routes = 0;
        routeCells = 0;
        walked = 0.0;
        pillarBlocks = 0;
        helpersRemoved = 0;
        aborts = 0;
        lastPosition = null;
    }

    /** A pause condition holds (screen, item use, low health, no material): let go of the keys. */
    void pause() {
        stopMining();
        controlling = false;
        holdStill = false;
        jumpThisTick = false;
    }

    /** The builder is about to click: stand still under the planner. */
    void holdForClick() {
        controlling = true;
        holdStill = true;
        jumpThisTick = false;
    }

    /** A block went in: spots that looked useless may not be any more. */
    void onPlaced() {
        rejected.clear();
        warnedUnreachable = false;
    }

    void onHelperPlaced(BlockPos pos, Block block) {
        helpers.put(pos.asLong(), block);
        pillarBlocks++;
    }

    void trackDistance(LocalPlayer player) {
        Vec3 position = player.position();
        if (lastPosition != null && mode != Mode.IDLE) {
            walked += Math.hypot(position.x - lastPosition.x, position.z - lastPosition.z)
                    + Math.abs(position.y - lastPosition.y);
        }
        lastPosition = position;
    }

    // ------------------------------------------------------------------ placing phase

    /**
     * The builder found nothing to click from here. Walk somewhere better or climb; returns a support block
     * click when one is due this tick.
     */
    HelperClick tickBuild(LocalPlayer player) {
        controlling = true;
        holdStill = false;
        jumpThisTick = false;
        if (mode == Mode.CLEARING) mode = Mode.IDLE;
        if (abortCooldown > 0) {
            abortCooldown--;
            holdStill = true;
            return null;
        }
        if (mode == Mode.PILLARING) return tickPillar(player);
        if (mode == Mode.WALKING) {
            if (followRoute(player)) return null;
            mode = Mode.IDLE;
            arrivalGrace = ARRIVAL_GRACE_TICKS;
        }
        holdStill = true;
        if (arrivalGrace > 0) {
            arrivalGrace--;
            return null;
        }
        if (hasGoal && feetKey(player) == goal) {
            // Arrived and still nothing to place: this spot only looked good on the grid.
            rejected.add(goal);
            hasGoal = false;
        }
        if (replanWait > 0) {
            replanWait--;
            return null;
        }
        if (!player.onGround()) return null;
        planBuild(player);
        return null;
    }

    private void planBuild(LocalPlayer player) {
        int count = host.walkTargets(player, targets);
        if (count <= 0) {
            replanWait = REPLAN_WAIT_TICKS;
            return;
        }
        WalkTerrain terrain = terrain();
        long start = feetKey(player);
        WalkPathfinder.Flood flood = WalkPathfinder.flood(terrain, start, MAX_DROP, FLOOD_COST, FLOOD_NODES);
        double reach = host.reach() - PLAN_MARGIN;
        StandingSpotChooser.Spot next = StandingSpotChooser.choose(terrain, flood, targets, count,
                StandingSpotChooser.Mode.PLACE, reach, spot, rejected::contains);
        if (next != null) {
            spot = next;
            startRoute(flood.pathTo(next.key()), next.key(), Mode.WALKING);
            return;
        }
        if (host.pillarEnabled()) {
            PillarPlanner.Pillar column = PillarPlanner.plan(terrain, flood, targets, count, reach, MAX_PILLAR,
                    host::inFootprint, rejected::contains);
            if (column != null) {
                pillar = column;
                pillarRetries = 0;
                pillarBaseY = Integer.MIN_VALUE;
                startRoute(flood.pathTo(WalkTerrain.pack(column.x(), column.y(), column.z())),
                        WalkTerrain.pack(column.x(), column.y(), column.z()), Mode.PILLARING);
                return;
            }
        }
        if (!warnedUnreachable) {
            warnedUnreachable = true;
            Notifier.warning(I18n.get("module.schematicbuilder.notify.unreachable"));
        }
        replanWait = REPLAN_WAIT_TICKS;
    }

    private void startRoute(List<Long> path, long target, Mode next) {
        if (path == null) {
            rejected.add(target);
            return;
        }
        route = path;
        // The first cell is where the player already stands.
        routeIndex = path.size() > 1 ? 1 : 0;
        goal = target;
        hasGoal = true;
        mode = next;
        bestWaypointDistance = Double.MAX_VALUE;
        stallTicks = 0;
        noProgressTicks = 0;
        routes++;
        routeCells += path.size() - 1;
    }

    // ------------------------------------------------------------------ route following

    /** Advances along the route; {@code false} once the last cell is reached (or the route was dropped). */
    private boolean followRoute(LocalPlayer player) {
        if (route == null) return false;
        Vec3 pos = player.position();
        while (routeIndex < route.size()) {
            long cell = route.get(routeIndex);
            boolean last = routeIndex == route.size() - 1;
            double distance = horizontalDistance(pos, cell);
            boolean level = Math.abs(pos.y - WalkTerrain.unpackY(cell)) < 0.6;
            if (!level || distance > (last ? ARRIVE_DISTANCE : PASS_DISTANCE) || last && !player.onGround()) break;
            routeIndex++;
            bestWaypointDistance = Double.MAX_VALUE;
            noProgressTicks = 0;
        }
        if (routeIndex >= route.size()) {
            route = null;
            return false;
        }
        if (blockedAhead()) {
            replan(player);
            return route != null;
        }
        double distance = horizontalDistance(pos, route.get(routeIndex));
        if (distance < bestWaypointDistance - 0.05) {
            bestWaypointDistance = distance;
            stallTicks = 0;
        } else if (++stallTicks > STALL_REPLAN_TICKS && player.onGround()) {
            stallTicks = 0;
            replan(player);
        }
        if (++noProgressTicks > host.stuckTimeout()) {
            abort();
            return false;
        }
        return route != null;
    }

    private boolean blockedAhead() {
        WalkTerrain terrain = terrain();
        for (int i = routeIndex; i < Math.min(route.size(), routeIndex + 3); i++) {
            long cell = route.get(i);
            if (!terrain.standable(WalkTerrain.unpackX(cell), WalkTerrain.unpackY(cell), WalkTerrain.unpackZ(cell))) return true;
        }
        return false;
    }

    private void replan(LocalPlayer player) {
        if (!player.onGround()) return;
        int drop = mode == Mode.CLEARING ? CLEAR_MAX_DROP : MAX_DROP;
        List<Long> path = WalkPathfinder.find(terrain(), feetKey(player), goal, drop, PATH_NODES);
        if (path == null) {
            rejected.add(goal);
            route = null;
            hasGoal = false;
            mode = Mode.IDLE;
            return;
        }
        route = path;
        routeIndex = path.size() > 1 ? 1 : 0;
        bestWaypointDistance = Double.MAX_VALUE;
        routeCells += path.size() - 1;
    }

    private void abort() {
        aborts++;
        Notifier.warning(I18n.get("module.schematicbuilder.notify.stuck"));
        if (hasGoal) rejected.add(goal);
        route = null;
        hasGoal = false;
        pillar = null;
        mode = Mode.IDLE;
        abortCooldown = ABORT_COOLDOWN_TICKS;
    }

    // ------------------------------------------------------------------ pillar

    private HelperClick tickPillar(LocalPlayer player) {
        PillarPlanner.Pillar column = pillar;
        if (column == null) {
            mode = Mode.IDLE;
            return null;
        }
        if (route != null && followRoute(player)) return null;
        if (route != null || mode != Mode.PILLARING) return null;
        int feetY = feetY(player);
        if (feetY >= column.topY() && player.onGround()) {
            finishPillar();
            return null;
        }
        int slot = helperSlot(player);
        if (slot < 0) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.no_helper"));
            finishPillar();
            abortCooldown = ABORT_COOLDOWN_TICKS;
            return null;
        }
        Vec3 pos = player.position();
        double off = Math.hypot(pos.x - (column.x() + 0.5), pos.z - (column.z() + 0.5));
        if (player.onGround()) {
            return pillarOnGround(player, column, feetY, off);
        }
        return pillarInAir(player, column, slot);
    }

    private HelperClick pillarOnGround(LocalPlayer player, PillarPlanner.Pillar column, int feetY, double off) {
        if (pillarBaseY != Integer.MIN_VALUE && feetY <= pillarBaseY && ++pillarRetries > MAX_PILLAR_RETRIES) {
            // Landed back where the jump started too often: the placement keeps missing.
            rejected.add(WalkTerrain.pack(column.x(), column.y(), column.z()));
            abort();
            return null;
        }
        pillarBaseY = Integer.MIN_VALUE;
        Vec3 down = new Vec3(column.x() + 0.5, feetY, column.z() + 0.5);
        Rotation look = Rotation.lookingAt(down, player.getEyePosition());
        host.aim(look);
        if (off > CENTER_DISTANCE) return null;
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        if (server == null || current == null || server.pitch() < PILLAR_PITCH_READY || current.pitch() < PILLAR_PITCH_READY) {
            return null;
        }
        if (Math.abs(player.getDeltaMovement().x) + Math.abs(player.getDeltaMovement().z) > 0.03) return null;
        jumpThisTick = true;
        pillarBaseY = feetY;
        return null;
    }

    private HelperClick pillarInAir(LocalPlayer player, PillarPlanner.Pillar column, int slot) {
        if (pillarBaseY == Integer.MIN_VALUE) return null;
        BlockPos target = new BlockPos(column.x(), pillarBaseY, column.z());
        Vec3 down = new Vec3(column.x() + 0.5, pillarBaseY, column.z() + 0.5);
        host.aim(Rotation.lookingAt(down, player.getEyePosition()));
        // The click is only legal once the feet have cleared the cell the new block goes into.
        if (player.getY() < pillarBaseY + 1.0 || !mc.level.getBlockState(target).canBeReplaced()) return null;
        ItemStack stack = player.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return null;
        BlockPos support = target.below();
        if (mc.level.getBlockState(support).canBeReplaced()) return null;
        Rotation rotation = Rotation.lookingAt(down, player.getEyePosition());
        double distanceSq = player.getEyePosition().distanceToSqr(down);
        BuildPlanner.Plan plan = new BuildPlanner.Plan(target, support, Direction.UP, down, rotation, false, distanceSq);
        return new HelperClick(plan, slot, blockItem.getBlock().defaultBlockState());
    }

    private void finishPillar() {
        pillar = null;
        pillarBaseY = Integer.MIN_VALUE;
        hasGoal = false;
        spot = null;
        mode = Mode.IDLE;
    }

    /** Hotbar slot of the support block: a listed helper (softest first), else any plain full block. */
    int helperSlot(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        Set<String> ids = host.helperIds();
        int best = -1;
        float bestHardness = Float.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!(stack.getItem() instanceof BlockItem blockItem) || !ids.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            float hardness = blockItem.getBlock().defaultDestroyTime();
            if (isPlainFullBlock(blockItem.getBlock()) && hardness < bestHardness) {
                bestHardness = hardness;
                best = i;
            }
        }
        if (best >= 0) return best;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof BlockItem blockItem && isPlainFullBlock(blockItem.getBlock())) return i;
        }
        return -1;
    }

    private static boolean isPlainFullBlock(Block block) {
        if (block instanceof FallingBlock || block.defaultDestroyTime() < 0.0f) return false;
        BlockState state = block.defaultBlockState();
        return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !BuildPlanner.isInteractive(state);
    }

    // ------------------------------------------------------------------ clearing support blocks

    /** The build is done: break the support blocks again. {@code false} once there is nothing left to do. */
    boolean tickClear(LocalPlayer player, double reach) {
        controlling = true;
        holdStill = true;
        jumpThisTick = false;
        prunePlacedHelpers();
        if (helpers.isEmpty()) {
            stopMining();
            if (mode != Mode.IDLE) Notifier.info(I18n.get("module.schematicbuilder.notify.helpers_removed"));
            mode = Mode.IDLE;
            controlling = false;
            return false;
        }
        if (mode != Mode.CLEARING) {
            mode = Mode.CLEARING;
            route = null;
            hasGoal = false;
            rejected.clear();
        }
        if (abortCooldown > 0) {
            abortCooldown--;
            return true;
        }
        if (route != null) {
            stopMining();
            holdStill = false;
            if (followRoute(player)) return true;
            holdStill = true;
            mode = Mode.CLEARING;
        }
        if (mineReachable(player, reach)) return true;
        if (!player.onGround()) return true;
        planClear(player, reach);
        return true;
    }

    private void prunePlacedHelpers() {
        Iterator<Map.Entry<Long, Block>> it = helpers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Block> entry = it.next();
            long key = entry.getKey();
            cursor.set(WalkTerrain.unpackX(key), WalkTerrain.unpackY(key), WalkTerrain.unpackZ(key));
            if (!mc.level.isLoaded(cursor)) continue;
            if (mc.level.getBlockState(cursor).getBlock() != entry.getValue()) {
                it.remove();
                if (mode == Mode.CLEARING) helpersRemoved++;
                if (mining != null && mining.asLong() == key) mining = null;
            }
        }
    }

    private boolean mineReachable(LocalPlayer player, double reach) {
        WalkTerrain terrain = terrain();
        Vec3 eye = player.getEyePosition();
        long best = 0L;
        int bestFace = -1;
        int bestY = Integer.MIN_VALUE;
        for (long key : helpers.keySet()) {
            int x = WalkTerrain.unpackX(key), y = WalkTerrain.unpackY(key), z = WalkTerrain.unpackZ(key);
            if (y <= bestY) continue;
            int face = ReachModel.breakFace(terrain, eye.x, eye.y, eye.z, x, y, z, reach);
            if (face < 0) continue;
            best = key;
            bestFace = face;
            bestY = y;
        }
        if (bestFace < 0) {
            stopMining();
            return false;
        }
        mine(player, BlockPos.of(best), bestFace);
        return true;
    }

    private void mine(LocalPlayer player, BlockPos pos, int faceIndex) {
        int[] n = ReachModel.faceNormal(faceIndex);
        if (mining != null && !mining.equals(pos)) stopMining();
        Vec3 eye = player.getEyePosition();
        Vec3 point = new Vec3(
                n[0] != 0 ? pos.getX() + 0.5 + n[0] * 0.5 : clamp(eye.x, pos.getX() + 0.2, pos.getX() + 0.8),
                n[1] != 0 ? pos.getY() + 0.5 + n[1] * 0.5 : clamp(eye.y, pos.getY() + 0.2, pos.getY() + 0.8),
                n[2] != 0 ? pos.getZ() + 0.5 + n[2] * 0.5 : clamp(eye.z, pos.getZ() + 0.2, pos.getZ() + 0.8));
        host.aim(Rotation.lookingAt(point, eye));
        BlockHitResult hit = BuildPlanner.trace(player, RotationManager.INSTANCE.getCurrentRotation(), host.reach() + 0.5);
        if (hit == null || !hit.getBlockPos().equals(pos)) return;
        if (mining == null) selectTool(player, mc.level.getBlockState(pos));
        mining = pos;
        miningFace = hit.getDirection();
        if (mc.gameMode.continueDestroyBlock(pos, miningFace)) player.swing(InteractionHand.MAIN_HAND);
    }

    private void selectTool(LocalPlayer player, BlockState state) {
        host.releaseHotbar();
        Inventory inventory = player.getInventory();
        int best = inventory.getSelectedSlot();
        float bestSpeed = inventory.getItem(best).getDestroySpeed(state);
        for (int i = 0; i < 9; i++) {
            float speed = inventory.getItem(i).getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        if (best != inventory.getSelectedSlot()) InventorySwap.INSTANCE.selectHotbar(best);
    }

    private void stopMining() {
        if (mining != null && mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        mining = null;
        miningFace = null;
    }

    private void planClear(LocalPlayer player, double reach) {
        if (replanWait > 0) {
            replanWait--;
            return;
        }
        int count = 0;
        for (long key : helpers.keySet()) {
            if (count == MAX_TARGETS) break;
            targets[count++] = key;
        }
        WalkTerrain terrain = terrain();
        WalkPathfinder.Flood flood = WalkPathfinder.flood(terrain, feetKey(player), CLEAR_MAX_DROP, FLOOD_COST, FLOOD_NODES);
        StandingSpotChooser.Spot next = StandingSpotChooser.choose(terrain, flood, targets, count,
                StandingSpotChooser.Mode.BREAK, reach - PLAN_MARGIN, null, rejected::contains);
        if (next == null) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.helpers_left", helpers.size()));
            helpers.clear();
            return;
        }
        if (next.key() == feetKey(player)) {
            rejected.add(next.key());
            replanWait = ARRIVAL_GRACE_TICKS;
            return;
        }
        startRoute(flood.pathTo(next.key()), next.key(), Mode.CLEARING);
    }

    // ------------------------------------------------------------------ input

    /** Drives the keys while the mover is in charge; leaves the player's own input alone otherwise. */
    void applyInput(MovementInputEvent event, LocalPlayer player) {
        if (!controlling) return;
        int keys = 0;
        boolean jump = false;
        boolean descending = false;
        if (!holdStill && route != null && routeIndex < route.size()) {
            long cell = route.get(routeIndex);
            Vec3 pos = player.position();
            double dx = WalkTerrain.unpackX(cell) + 0.5 - pos.x;
            double dz = WalkTerrain.unpackZ(cell) + 0.5 - pos.z;
            keys = Steering.keysToward(RotationManager.INSTANCE.getMovementRotation().yaw(), dx, dz);
            int y = WalkTerrain.unpackY(cell);
            jump = y > pos.y + 0.5 && player.onGround() && Math.hypot(dx, dz) < 1.4;
            descending = y < pos.y - 0.5;
        } else if (mode == Mode.PILLARING && pillar != null && route == null && player.onGround()) {
            // Centre on the column before the jump; the pillar is one block wide.
            Vec3 pos = player.position();
            double dx = pillar.x() + 0.5 - pos.x;
            double dz = pillar.z() + 0.5 - pos.z;
            if (Math.hypot(dx, dz) > CENTER_DISTANCE * 0.5) {
                keys = Steering.keysToward(RotationManager.INSTANCE.getMovementRotation().yaw(), dx, dz);
            }
        }
        if (jumpThisTick && player.onGround()) jump = true;
        if (player.isSprinting()) player.setSprinting(false);
        event.setForward((keys & Steering.FORWARD) != 0);
        event.setBackward((keys & Steering.BACKWARD) != 0);
        event.setLeft((keys & Steering.LEFT) != 0);
        event.setRight((keys & Steering.RIGHT) != 0);
        event.setJump(jump);
        event.setSprint(false);
        event.setSneak(!descending && player.onGround() && atEdge(player));
        jumpThisTick = false;
    }

    /** Any corner of the feet hangs over a cell with nothing to stand on: a step further would fall. */
    private boolean atEdge(LocalPlayer player) {
        ClientLevel level = mc.level;
        AABB box = player.getBoundingBox();
        double y = box.minY - 0.5;
        double[] xs = {box.minX + 0.05, box.maxX - 0.05};
        double[] zs = {box.minZ + 0.05, box.maxZ - 0.05};
        for (double x : xs) {
            for (double z : zs) {
                cursor.set(Math.floor(x), Math.floor(y), Math.floor(z));
                if (level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ world view

    private WalkTerrain terrain() {
        ClientLevel level = mc.level;
        Map<Long, Byte> cache = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return (x, y, z) -> {
            long key = WalkTerrain.pack(x, y, z);
            Byte known = cache.get(key);
            if (known != null) return known;
            byte value = classify(level, pos.set(x, y, z));
            cache.put(key, value);
            return value;
        };
    }

    static byte classify(ClientLevel level, BlockPos pos) {
        if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY() || !level.isLoaded(pos)) return WalkTerrain.UNKNOWN;
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || isDangerous(state.getBlock())) return WalkTerrain.DANGER;
        if (state.getBlock() instanceof DoorBlock) return state.getValue(DoorBlock.OPEN) ? WalkTerrain.OPEN : WalkTerrain.SOLID;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return WalkTerrain.OPEN;
        double top = shape.max(Direction.Axis.Y);
        if (top > 1.0) return WalkTerrain.UNKNOWN;
        if (top < 0.2) return WalkTerrain.OPEN;
        return WalkTerrain.SOLID;
    }

    private static boolean isDangerous(Block block) {
        return block instanceof BaseFireBlock
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.CACTUS
                || block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.SWEET_BERRY_BUSH
                || block == Blocks.POWDER_SNOW
                || block == Blocks.WITHER_ROSE
                || block == Blocks.COBWEB
                || block == Blocks.POINTED_DRIPSTONE;
    }

    private static int feetY(LocalPlayer player) {
        // Standing on a slab puts the feet half way into a cell; round so the cell below is the slab.
        return (int) Math.floor(player.getY() + 0.5);
    }

    private static long feetKey(LocalPlayer player) {
        return WalkTerrain.pack((int) Math.floor(player.getX()), feetY(player), (int) Math.floor(player.getZ()));
    }

    private static double horizontalDistance(Vec3 pos, long cell) {
        return Math.hypot(WalkTerrain.unpackX(cell) + 0.5 - pos.x, WalkTerrain.unpackZ(cell) + 0.5 - pos.z);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Debug view for the smoke driver: mode, route progress and the current goal. */
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        parts.add("mode=" + mode);
        if (route != null) parts.add("route=" + routeIndex + "/" + route.size());
        if (hasGoal) parts.add("goal=" + BlockPos.of(goal).toShortString());
        if (pillar != null) parts.add("pillar=" + pillar);
        parts.add("helpers=" + helpers.size());
        return String.join(" ", parts);
    }
}
