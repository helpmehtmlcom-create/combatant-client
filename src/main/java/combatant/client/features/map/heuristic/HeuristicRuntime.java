/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.heuristic;

import combatant.client.config.subsystem.MapHeuristicConfig;
import combatant.client.config.subsystem.MapTriangulationConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class HeuristicRuntime {
    private static final HeuristicRuntime INSTANCE = new HeuristicRuntime();

    private final MapHeuristicConfig config = MapHeuristicConfig.get();
    private final MapTriangulationConfig modeConfig = MapTriangulationConfig.get();
    private final ConcurrentHashMap<UUID, TargetState> states = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> suppressedTargets = ConcurrentHashMap.newKeySet();
    private final ArrayBlockingQueue<UUID> queue = new ArrayBlockingQueue<>(256);
    private final AtomicReference<Map<UUID, HeuristicEstimate>> published = new AtomicReference<>(Map.of());
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejectedMode = new AtomicLong();
    private final AtomicLong rejectedAge = new AtomicLong();
    private final AtomicLong rejectedDuplicate = new AtomicLong();
    private final AtomicLong rejectedInformationGain = new AtomicLong();
    private final AtomicLong rejectedCapacity = new AtomicLong();
    private final AtomicLong solved = new AtomicLong();
    private final AtomicLong segmentResets = new AtomicLong();

    private final Thread worker;

    private HeuristicRuntime() {
        worker = new Thread(this::loop, "Combatant-Map-Heuristic");
        worker.setDaemon(true);
        worker.start();
    }

    public static HeuristicRuntime get() { return INSTANCE; }

    public Map<UUID, HeuristicEstimate> snapshot() {
        MapTriangulationMode mode = modeConfig.mode();
        if (mode == MapTriangulationMode.OFF) return Map.of();
        Map<UUID, HeuristicEstimate> current = published.get();
        if (current.isEmpty()) return current;
        long now = System.currentTimeMillis();
        Map<UUID, HeuristicEstimate> filtered = new LinkedHashMap<>();
        for (Map.Entry<UUID, HeuristicEstimate> entry : current.entrySet()) {
            HeuristicEstimate estimate = entry.getValue();
            if (estimate == null || now - estimate.updatedAtMs() > config.maxSampleAgeMs()) continue;
            if (mode == MapTriangulationMode.TARGETED && !modeConfig.isTargeted(entry.getKey())) continue;
            filtered.put(entry.getKey(), estimate);
        }
        return filtered.size() == current.size() ? current : Map.copyOf(filtered);
    }

    public HeuristicEstimate estimate(UUID id) {
        if (id == null || !modeConfig.accepts(id)) return null;
        return published.get().get(id);
    }

    public HeuristicRuntimeStats stats() {
        int queued = 0;
        for (TargetState state : states.values()) if (state.queued.get()) queued++;
        return new HeuristicRuntimeStats(modeConfig.mode(), accepted.get(), rejectedMode.get(), rejectedAge.get(),
                rejectedDuplicate.get(), rejectedInformationGain.get(), rejectedCapacity.get(), solved.get(),
                segmentResets.get(), states.size(), queued);
    }

    public boolean offer(HeuristicObservation observation) {
        if (observation == null || observation.targetUuid() == null || !config.enabled()) {
            rejectedMode.incrementAndGet();
            return false;
        }
        UUID target = observation.targetUuid();
        if (!modeConfig.accepts(target) || suppressedTargets.contains(target)) {
            rejectedMode.incrementAndGet();
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - observation.observedAtMs() > config.maxSampleAgeMs()) {
            rejectedAge.incrementAndGet();
            return false;
        }

        if (modeConfig.mode() == MapTriangulationMode.DATA_MINING
                && !states.containsKey(target)
                && states.size() >= modeConfig.dataMiningMaxActiveTargets()) {
            rejectedCapacity.incrementAndGet();
            return false;
        }

        TargetState state = states.computeIfAbsent(target, ignored -> new TargetState());
        synchronized (state) {
            prune(state, now);
            HeuristicObservation last = state.samples.peekLast();
            if (last != null) {
                if (observation.sourceRevision() == last.sourceRevision()) {
                    rejectedDuplicate.incrementAndGet();
                    return false;
                }
                double baseline = Math.hypot(observation.observerX() - last.observerX(),
                        observation.observerZ() - last.observerZ());
                double angle = angleDiff(observation.bearingRadians(), last.bearingRadians());
                if (baseline < config.minBaseline()
                        && angle < Math.toRadians(config.minBearingDeltaDegrees())) {
                    rejectedInformationGain.incrementAndGet();
                    return false;
                }
            }

            state.samples.addLast(observation);
            while (state.samples.size() > config.maxSamplesPerTarget()) state.samples.removeFirst();
            state.dirty = true;
            accepted.incrementAndGet();

            boolean solveDue = state.estimate == null
                    || now - state.lastSolvedAt >= modeConfig.minSolveIntervalMs();
            if (solveDue) enqueue(target, state);
            return true;
        }
    }

    /**
     * Exact sources suppress new triangulation work without destroying the current segment/estimate.
     * This preserves history and lets triangulation resume when the exact source disappears.
     */
    public void setSuppressedTargets(java.util.Set<UUID> targets) {
        suppressedTargets.clear();
        if (targets != null && !targets.isEmpty()) suppressedTargets.addAll(targets);
    }

    public boolean isSuppressed(UUID id) {
        return id != null && suppressedTargets.contains(id);
    }

    public void clear(UUID id) {
        if (id == null) return;
        suppressedTargets.remove(id);
        states.remove(id);
        updateMap(id, null);
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        worker.interrupt();
        states.clear();
        suppressedTargets.clear();
        published.set(Map.of());
    }

    private void loop() {
        while (running.get()) {
            try {
                UUID id = queue.poll(100L, TimeUnit.MILLISECONDS);
                if (id == null) {
                    enqueueDueDirty();
                    continue;
                }
                TargetState state = states.get(id);
                if (state == null) continue;
                if (suppressedTargets.contains(id) || !modeConfig.accepts(id)) {
                    state.queued.set(false);
                    continue;
                }

                List<HeuristicObservation> samples;
                long segment;
                synchronized (state) {
                    state.queued.set(false);
                    prune(state, System.currentTimeMillis());
                    samples = new ArrayList<>(state.samples);
                    segment = state.segmentId;
                    state.dirty = false;
                }
                if (samples.size() < 2) continue;

                HeuristicEstimate next = HeuristicSolver.solve(id, samples, segment, config.forwardRejectTolerance(), Math.toRadians(config.bearingNoiseDegrees()));
                if (next == null) continue;

                synchronized (state) {
                    if (state.estimate != null && shouldReset(state.estimate, next)) {
                        state.segmentId++;
                        segmentResets.incrementAndGet();
                        List<HeuristicObservation> latestSamples = new ArrayList<>(state.samples);
                        state.samples.clear();
                        for (int i = Math.max(0, latestSamples.size() - 3); i < latestSamples.size(); i++) {
                            state.samples.addLast(latestSamples.get(i));
                        }
                        next = HeuristicSolver.solve(id, new ArrayList<>(state.samples),
                                state.segmentId, config.forwardRejectTolerance(), Math.toRadians(config.bearingNoiseDegrees()));
                        if (next == null) continue;
                    }
                    state.estimate = next;
                    state.lastSolvedAt = System.currentTimeMillis();
                }
                solved.incrementAndGet();
                updateMap(id, next);
            } catch (InterruptedException interrupted) {
                if (!running.get()) return;
            } catch (RuntimeException ignored) {
            }
        }
    }


    private void enqueue(UUID id, TargetState state) {
        if (state.queued.compareAndSet(false, true) && !queue.offer(id)) {
            state.queued.set(false);
        }
    }

    /**
     * Coalesced observations received inside the per-mode solve interval must still be solved even if no
     * later locator packet arrives. The worker periodically promotes only dirty, due targets; this keeps
     * DATA_MINING bounded without spawning per-target timers/threads.
     */
    private void enqueueDueDirty() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, TargetState> entry : states.entrySet()) {
            UUID id = entry.getKey();
            TargetState state = entry.getValue();
            if (suppressedTargets.contains(id) || !modeConfig.accepts(id)) continue;
            synchronized (state) {
                prune(state, now);
                if (!state.dirty || state.samples.size() < 2) continue;
                if (state.estimate != null && now - state.lastSolvedAt < modeConfig.minSolveIntervalMs()) continue;
                enqueue(id, state);
            }
        }
    }

    private boolean shouldReset(HeuristicEstimate a, HeuristicEstimate b) {
        double shift = Math.hypot(a.x() - b.x(), a.z() - b.z());
        double expected = Math.max(a.uncertaintyMajor(), b.uncertaintyMajor());
        return shift > Math.max(config.segmentResetDistance(), expected * config.segmentResetSigma())
                && b.confidence() >= config.segmentResetMinConfidence();
    }

    private void updateMap(UUID id, HeuristicEstimate value) {
        while (true) {
            Map<UUID, HeuristicEstimate> current = published.get();
            Map<UUID, HeuristicEstimate> next = new LinkedHashMap<>(current);
            if (value == null) next.remove(id);
            else next.put(id, value);
            if (published.compareAndSet(current, Map.copyOf(next))) return;
        }
    }

    private void prune(TargetState state, long now) {
        while (!state.samples.isEmpty()
                && now - state.samples.peekFirst().observedAtMs() > config.maxSampleAgeMs()) {
            state.samples.removeFirst();
        }
    }

    private static double angleDiff(double a, double b) {
        double d = Math.abs(a - b) % (Math.PI * 2.0);
        return d > Math.PI ? Math.PI * 2.0 - d : d;
    }

    private static final class TargetState {
        final ArrayDeque<HeuristicObservation> samples = new ArrayDeque<>();
        final AtomicBoolean queued = new AtomicBoolean();
        long segmentId = 1;
        long lastSolvedAt;
        HeuristicEstimate estimate;
        boolean dirty;
    }
}
