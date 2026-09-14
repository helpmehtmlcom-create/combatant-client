/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Single immutable publication point for map/HUD consumers. */
public final class PlayerLocationService {
    private static final PlayerLocationService INSTANCE = new PlayerLocationService();
    private static final int EVENT_HISTORY_LIMIT = 512;

    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<PlayerLocationView> published = new AtomicReference<>(PlayerLocationView.empty());
    private final AtomicReference<List<PlayerLocationEvent>> events = new AtomicReference<>(List.of());

    private PlayerLocationService() {}
    public static PlayerLocationService get() { return INSTANCE; }
    public PlayerLocationView snapshot() { return published.get(); }

    /** Recent bounded event history, not merely the events from the last tick. */
    public List<PlayerLocationEvent> events() { return events.get(); }

    public List<PlayerLocationEvent> eventsSince(long generationExclusive) {
        return events.get().stream().filter(event -> event.generation() > generationExclusive).toList();
    }

    public PlayerLocationEvent latestLocatorEvent(UUID player, PlayerLocationEventType type) {
        if (player == null || type == null) return null;
        List<PlayerLocationEvent> history = events.get();
        for (int i = history.size() - 1; i >= 0; i--) {
            PlayerLocationEvent event = history.get(i);
            if (!player.equals(event.playerUuid()) || event.type() != type) continue;
            PlayerLocationSnapshot snapshot = event.current() != null ? event.current() : event.previous();
            if (snapshot != null && isLocator(snapshot.source())) return event;
        }
        return null;
    }

    private static boolean isLocator(PlayerLocationSource source) {
        return source == PlayerLocationSource.LOCATOR_EXACT
                || source == PlayerLocationSource.LOCATOR_APPROXIMATE
                || source == PlayerLocationSource.LOCATOR_BEARING;
    }

    public List<PlayerLocationEvent> publish(List<PlayerLocationSnapshot> snapshots) {
        long now = System.currentTimeMillis();
        Map<UUID, List<PlayerLocationSnapshot>> grouped = new LinkedHashMap<>();
        if (snapshots != null) {
            for (PlayerLocationSnapshot snapshot : snapshots) {
                if (snapshot == null || snapshot.playerUuid() == null) continue;
                grouped.computeIfAbsent(snapshot.playerUuid(), ignored -> new ArrayList<>()).add(snapshot);
            }
        }

        Map<UUID, PlayerLocationSnapshot> best = new LinkedHashMap<>();
        Map<UUID, List<PlayerLocationSnapshot>> immutableSources = new LinkedHashMap<>();
        Comparator<PlayerLocationSnapshot> comparator = Comparator
                .comparingInt((PlayerLocationSnapshot value) -> value.source().priority()).reversed()
                .thenComparing((PlayerLocationSnapshot value) -> value.hasPosition() ? 0 : 1)
                .thenComparing(Comparator.comparingDouble(PlayerLocationSnapshot::confidence).reversed())
                .thenComparing(Comparator.comparingLong(PlayerLocationSnapshot::observedAtMs).reversed());

        for (Map.Entry<UUID, List<PlayerLocationSnapshot>> entry : grouped.entrySet()) {
            List<PlayerLocationSnapshot> values = entry.getValue();
            values.sort(comparator);
            List<PlayerLocationSnapshot> copy = List.copyOf(values);
            immutableSources.put(entry.getKey(), copy);
            if (!copy.isEmpty()) best.put(entry.getKey(), copy.getFirst());
        }

        PlayerLocationView previousView = published.get();
        long nextGeneration = generation.incrementAndGet();
        PlayerLocationView nextView = new PlayerLocationView(nextGeneration, now, Map.copyOf(best), Map.copyOf(immutableSources));
        List<PlayerLocationEvent> delta = computeEvents(nextGeneration, now, previousView, nextView);
        published.set(nextView);
        appendEvents(delta);
        return delta;
    }

    public List<PlayerLocationEvent> clear() {
        long now = System.currentTimeMillis();
        PlayerLocationView previous = published.get();
        long nextGeneration = generation.incrementAndGet();
        PlayerLocationView next = new PlayerLocationView(nextGeneration, now, Map.of(), Map.of());
        List<PlayerLocationEvent> delta = computeEvents(nextGeneration, now, previous, next);
        published.set(next);
        appendEvents(delta);
        return delta;
    }

    private void appendEvents(List<PlayerLocationEvent> delta) {
        if (delta == null || delta.isEmpty()) return;
        while (true) {
            List<PlayerLocationEvent> current = events.get();
            ArrayList<PlayerLocationEvent> next = new ArrayList<>(Math.min(EVENT_HISTORY_LIMIT, current.size() + delta.size()));
            int keepFrom = Math.max(0, current.size() + delta.size() - EVENT_HISTORY_LIMIT);
            if (keepFrom < current.size()) next.addAll(current.subList(keepFrom, current.size()));
            int deltaFrom = Math.max(0, keepFrom - current.size());
            next.addAll(delta.subList(deltaFrom, delta.size()));
            if (events.compareAndSet(current, List.copyOf(next))) return;
        }
    }

    private static List<PlayerLocationEvent> computeEvents(long generation, long now,
                                                            PlayerLocationView previous, PlayerLocationView current) {
        Map<UUID, PlayerLocationSnapshot> before = previous == null ? Map.of() : previous.bestByPlayer();
        Map<UUID, PlayerLocationSnapshot> after = current == null ? Map.of() : current.bestByPlayer();
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        if (previous != null) ids.addAll(previous.sourcesByPlayer().keySet());
        if (current != null) ids.addAll(current.sourcesByPlayer().keySet());
        List<PlayerLocationEvent> out = new ArrayList<>();

        for (UUID id : ids) {
            PlayerLocationSnapshot oldBest = before.get(id);
            PlayerLocationSnapshot nextBest = after.get(id);
            String name = nextBest != null && !nextBest.playerName().isBlank()
                    ? nextBest.playerName() : oldBest != null ? oldBest.playerName() : "";

            Map<SourceIdentity, PlayerLocationSnapshot> oldSources = sourceMap(previous == null ? List.of() : previous.sources(id));
            Map<SourceIdentity, PlayerLocationSnapshot> newSources = sourceMap(current == null ? List.of() : current.sources(id));
            java.util.LinkedHashSet<SourceIdentity> sourceIds = new java.util.LinkedHashSet<>();
            sourceIds.addAll(oldSources.keySet()); sourceIds.addAll(newSources.keySet());
            for (SourceIdentity sourceId : sourceIds) {
                PlayerLocationSnapshot oldSource = oldSources.get(sourceId);
                PlayerLocationSnapshot newSource = newSources.get(sourceId);
                if (oldSource == null && newSource != null) {
                    out.add(new PlayerLocationEvent(generation, now, id, name,
                            PlayerLocationEventType.SOURCE_APPEARED, null, newSource));
                } else if (oldSource != null && newSource == null) {
                    out.add(new PlayerLocationEvent(generation, now, id, name,
                            PlayerLocationEventType.SOURCE_LOST, oldSource, null));
                }
            }

            if (oldBest == null && nextBest != null) {
                out.add(new PlayerLocationEvent(generation, now, id, name, PlayerLocationEventType.APPEARED, null, nextBest));
                if (nextBest.exact()) out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.EXACT_SOURCE_APPEARED, null, nextBest));
                continue;
            }
            if (oldBest != null && nextBest == null) {
                out.add(new PlayerLocationEvent(generation, now, id, name, PlayerLocationEventType.DISAPPEARED, oldBest, null));
                if (oldBest.exact()) out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.EXACT_SOURCE_LOST, oldBest, null));
                continue;
            }
            if (oldBest == null) continue;

            if (oldBest.source() != nextBest.source() || !oldBest.sourceKey().equals(nextBest.sourceKey())
                    || !oldBest.worldKey().equals(nextBest.worldKey())) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.SOURCE_CHANGED, oldBest, nextBest));
            }
            if (!oldBest.exact() && nextBest.exact()) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.EXACT_SOURCE_APPEARED, oldBest, nextBest));
            } else if (oldBest.exact() && !nextBest.exact()) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.EXACT_SOURCE_LOST, oldBest, nextBest));
            }
        }
        return List.copyOf(out);
    }

    private static Map<SourceIdentity, PlayerLocationSnapshot> sourceMap(List<PlayerLocationSnapshot> snapshots) {
        Map<SourceIdentity, PlayerLocationSnapshot> out = new LinkedHashMap<>();
        for (PlayerLocationSnapshot snapshot : snapshots) {
            SourceIdentity key = new SourceIdentity(snapshot.source(), snapshot.sourceKey(), snapshot.worldKey());
            PlayerLocationSnapshot previous = out.get(key);
            if (previous == null || snapshot.observedAtMs() > previous.observedAtMs()) out.put(key, snapshot);
        }
        return out;
    }

    private record SourceIdentity(PlayerLocationSource source, String sourceKey, String worldKey) {}
}
