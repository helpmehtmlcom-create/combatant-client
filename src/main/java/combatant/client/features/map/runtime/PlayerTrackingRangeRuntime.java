/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.runtime;

import combatant.client.features.map.location.LocationSessionKey;
import combatant.client.features.map.locator.LocatorObservation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Learns the effective distance at which this client receives player entities.
 *
 * <p>Simulation distance and effective chunk view distance are directly observable. The exact
 * server player tracking range is not: vanilla additionally applies a server-side entity broadcast
 * scale which is not sent to clients. We therefore keep a hard lower bound (furthest player entity
 * actually observed) and a robust boundary estimate from local-entity appear/disappear transitions
 * while the player remains present in TAB/locator.</p>
 */
public final class PlayerTrackingRangeRuntime {
    private static final PlayerTrackingRangeRuntime INSTANCE = new PlayerTrackingRangeRuntime();
    private static final int MAX_BOUNDARY_SAMPLES = 32;
    private static final long TRANSITION_MAX_AGE_MS = 1_750L;
    private static final double MIN_USEFUL_DISTANCE = 48.0;

    private final AtomicReference<PlayerTrackingRangeSnapshot> published =
            new AtomicReference<>(PlayerTrackingRangeSnapshot.empty());
    private final Map<UUID, VisibilityState> states = new HashMap<>();
    private final ArrayDeque<Double> boundarySamples = new ArrayDeque<>();

    private LocationSessionKey activeSession;
    private double confirmedLowerBound;

    private PlayerTrackingRangeRuntime() {}

    public static PlayerTrackingRangeRuntime get() { return INSTANCE; }
    public PlayerTrackingRangeSnapshot snapshot() { return published.get(); }

    public synchronized void capture(Minecraft mc, LocationSessionKey session, List<LocatorObservation> locator) {
        if (mc == null || mc.player == null || mc.level == null || mc.getConnection() == null || session == null) {
            clear();
            return;
        }
        if (!session.equals(activeSession)) resetForSession(session);

        long now = System.currentTimeMillis();
        UUID self = mc.player.getUUID();
        Set<UUID> locatorPresent = new HashSet<>();
        if (locator != null) {
            for (LocatorObservation observation : locator) {
                if (observation != null && observation.targetUuid() != null) locatorPresent.add(observation.targetUuid());
            }
        }
        Set<UUID> online = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().id() != null) {
                online.add(info.getProfile().id());
            }
        }

        int simulationChunks = Math.max(0, mc.level.getServerSimulationDistance());
        int viewChunks = Math.max(0, mc.options.getEffectiveRenderDistance());
        double simulationBlocks = simulationChunks * 16.0;
        double viewBlocks = viewChunks * 16.0;
        double vanillaTrackingCap = Math.min(EntityTypes.PLAYER.clientTrackingRange() * 16.0, viewBlocks);

        Set<UUID> continuityPresent = new HashSet<>(online);
        continuityPresent.addAll(locatorPresent);
        continuityPresent.remove(self);
        // Merely being listed in TAB/locator is not a boundary sample. It only lets us observe an
        // absent -> local transition later; this avoids treating every already-loaded player on the
        // first tick after join as if they had just crossed the tracking boundary.
        for (UUID id : continuityPresent) states.computeIfAbsent(id, ignored -> new VisibilityState());

        Set<UUID> localNow = new HashSet<>();
        double observerX = mc.player.getX();
        double observerZ = mc.player.getZ();
        for (Player player : mc.level.players()) {
            if (player == null || player.getUUID() == null || player.getUUID().equals(self)) continue;
            UUID id = player.getUUID();
            localNow.add(id);
            double distance = Math.hypot(player.getX() - observerX, player.getZ() - observerZ);
            // View distance is a protocol/client-observable hard envelope for normal tracked player
            // delivery. Ignore absurd one-tick coordinates outside it: those can occur around an
            // entity teleport/remove sequence and must not permanently inflate our safe lower bound.
            if (Double.isFinite(distance) && (viewBlocks <= 0.0 || distance <= viewBlocks + 32.0)) {
                confirmedLowerBound = Math.max(confirmedLowerBound, distance);
            }

            VisibilityState state = states.computeIfAbsent(id, ignored -> new VisibilityState());
            if (!state.localVisible && state.observedAbsent && continuityPresent.contains(id)) {
                maybeRecordBoundary(distance, viewBlocks);
            }
            state.localVisible = true;
            state.observedAbsent = false;
            state.lastSeenAtMs = now;
            state.lastSeenDistance = distance;
        }

        for (Map.Entry<UUID, VisibilityState> entry : states.entrySet()) {
            UUID id = entry.getKey();
            VisibilityState state = entry.getValue();
            if (localNow.contains(id)) continue;
            boolean continuity = continuityPresent.contains(id);
            if (state.localVisible && continuity && now - state.lastSeenAtMs <= TRANSITION_MAX_AGE_MS) {
                maybeRecordBoundary(state.lastSeenDistance, viewBlocks);
            }
            state.localVisible = false;
            state.observedAbsent = continuity;
        }
        states.keySet().removeIf(id -> !continuityPresent.contains(id) && !localNow.contains(id));

        double boundary = robustBoundary();
        published.set(new PlayerTrackingRangeSnapshot(now, simulationChunks, simulationBlocks,
                viewChunks, viewBlocks, vanillaTrackingCap, confirmedLowerBound, boundary, boundarySamples.size()));
    }

    /**
     * Returns true only for a conservative contradiction: the whole uncertainty area is well inside
     * a radius at which this session has already received other player entities.
     */
    public boolean contradictsLocalAbsence(Minecraft mc, double targetX, double targetZ, double uncertaintyMajor) {
        if (mc == null || mc.player == null || !Double.isFinite(targetX) || !Double.isFinite(targetZ)) return false;
        PlayerTrackingRangeSnapshot snapshot = published.get();
        double guaranteed = snapshot.conservativeGuaranteedVisibilityRadius(24.0);
        if (guaranteed < 64.0) return false;
        double distance = Math.hypot(targetX - mc.player.getX(), targetZ - mc.player.getZ());
        double uncertainty = Math.max(0.0, Double.isFinite(uncertaintyMajor) ? uncertaintyMajor : 0.0);
        return distance + uncertainty + 8.0 < guaranteed;
    }

    public synchronized void clear() {
        activeSession = null;
        states.clear();
        boundarySamples.clear();
        confirmedLowerBound = 0.0;
        published.set(PlayerTrackingRangeSnapshot.empty());
    }

    private void resetForSession(LocationSessionKey session) {
        activeSession = session;
        states.clear();
        boundarySamples.clear();
        confirmedLowerBound = 0.0;
        published.set(PlayerTrackingRangeSnapshot.empty());
    }

    private void maybeRecordBoundary(double distance, double viewBlocks) {
        if (!Double.isFinite(distance) || distance < MIN_USEFUL_DISTANCE) return;
        // A teleport at 10-30 blocks must not poison the learned boundary. Once a larger local
        // distance has been observed, only transitions reasonably close to that envelope qualify.
        double floor = Math.max(MIN_USEFUL_DISTANCE, confirmedLowerBound * 0.60);
        if (distance < floor) return;
        if (viewBlocks > 0.0 && distance > viewBlocks + 48.0) return;
        boundarySamples.addLast(distance);
        while (boundarySamples.size() > MAX_BOUNDARY_SAMPLES) boundarySamples.removeFirst();
    }

    private double robustBoundary() {
        if (boundarySamples.size() < 3) return Double.NaN;
        List<Double> values = new ArrayList<>(boundarySamples);
        values.sort(Double::compareTo);
        double median = median(values);
        List<Double> deviations = new ArrayList<>(values.size());
        for (double value : values) deviations.add(Math.abs(value - median));
        deviations.sort(Double::compareTo);
        double mad = median(deviations);
        if (mad < 1.0) return median;
        double limit = Math.max(16.0, mad * 3.5);
        List<Double> filtered = values.stream().filter(v -> Math.abs(v - median) <= limit).toList();
        return filtered.size() < 3 ? median : median(filtered);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        int mid = values.size() / 2;
        return (values.size() & 1) == 1 ? values.get(mid) : (values.get(mid - 1) + values.get(mid)) * 0.5;
    }

    private static final class VisibilityState {
        boolean localVisible;
        boolean observedAbsent;
        long lastSeenAtMs;
        double lastSeenDistance;
    }
}
