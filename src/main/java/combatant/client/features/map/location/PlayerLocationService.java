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

/**
 * Single immutable publication point for map/HUD consumers. It never reads Minecraft state itself.
 * Source producers remain independent and are merged by priority/freshness only here.
 */
public final class PlayerLocationService {
    private static final PlayerLocationService INSTANCE = new PlayerLocationService();

    private final AtomicLong generation = new AtomicLong();
    private final AtomicReference<PlayerLocationView> published =
            new AtomicReference<>(PlayerLocationView.empty());
    private final AtomicReference<List<PlayerLocationEvent>> events = new AtomicReference<>(List.of());

    private PlayerLocationService() {
    }

    public static PlayerLocationService get() {
        return INSTANCE;
    }

    public PlayerLocationView snapshot() {
        return published.get();
    }

    public List<PlayerLocationEvent> events() {
        return events.get();
    }

    public void publish(List<PlayerLocationSnapshot> snapshots) {
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
        PlayerLocationView nextView = new PlayerLocationView(nextGeneration, now,
                Map.copyOf(best), Map.copyOf(immutableSources));
        events.set(computeEvents(nextGeneration, now, previousView, nextView));
        published.set(nextView);
    }

    public void clear() {
        long now = System.currentTimeMillis();
        PlayerLocationView previous = published.get();
        long nextGeneration = generation.incrementAndGet();
        PlayerLocationView next = new PlayerLocationView(nextGeneration, now, Map.of(), Map.of());
        events.set(computeEvents(nextGeneration, now, previous, next));
        published.set(next);
    }

    private static List<PlayerLocationEvent> computeEvents(long generation,
                                                            long now,
                                                            PlayerLocationView previous,
                                                            PlayerLocationView current) {
        Map<UUID, PlayerLocationSnapshot> before = previous == null ? Map.of() : previous.bestByPlayer();
        Map<UUID, PlayerLocationSnapshot> after = current == null ? Map.of() : current.bestByPlayer();
        java.util.LinkedHashSet<UUID> ids = new java.util.LinkedHashSet<>();
        ids.addAll(before.keySet());
        ids.addAll(after.keySet());
        List<PlayerLocationEvent> out = new ArrayList<>();
        for (UUID id : ids) {
            PlayerLocationSnapshot old = before.get(id);
            PlayerLocationSnapshot next = after.get(id);
            String name = next != null && !next.playerName().isBlank()
                    ? next.playerName() : old != null ? old.playerName() : "";

            if (old == null && next != null) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.APPEARED, null, next));
                if (next.exact()) {
                    out.add(new PlayerLocationEvent(generation, now, id, name,
                            PlayerLocationEventType.EXACT_SOURCE_APPEARED, null, next));
                }
                continue;
            }
            if (old != null && next == null) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.DISAPPEARED, old, null));
                if (old.exact()) {
                    out.add(new PlayerLocationEvent(generation, now, id, name,
                            PlayerLocationEventType.EXACT_SOURCE_LOST, old, null));
                }
                continue;
            }
            if (old == null) continue;

            if (old.source() != next.source() || !old.sourceKey().equals(next.sourceKey())
                    || !old.worldKey().equals(next.worldKey())) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.SOURCE_CHANGED, old, next));
            }
            if (!old.exact() && next.exact()) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.EXACT_SOURCE_APPEARED, old, next));
            } else if (old.exact() && !next.exact()) {
                out.add(new PlayerLocationEvent(generation, now, id, name,
                        PlayerLocationEventType.EXACT_SOURCE_LOST, old, next));
            }
        }
        return List.copyOf(out);
    }
}
