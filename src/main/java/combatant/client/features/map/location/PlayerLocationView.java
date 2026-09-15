/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PlayerLocationView(
        long generation,
        long publishedAtMs,
        Map<UUID, PlayerLocationSnapshot> bestByPlayer,
        Map<UUID, List<PlayerLocationSnapshot>> sourcesByPlayer
) {
    public static PlayerLocationView empty() {
        return new PlayerLocationView(0L, 0L, Map.of(), Map.of());
    }

    public PlayerLocationSnapshot best(UUID player) {
        return player == null ? null : bestByPlayer.get(player);
    }

    public List<PlayerLocationSnapshot> sources(UUID player) {
        return player == null ? List.of() : sourcesByPlayer.getOrDefault(player, List.of());
    }

    public PlayerLocationSnapshot source(UUID player, PlayerLocationSource source) {
        if (player == null || source == null) return null;
        for (PlayerLocationSnapshot snapshot : sources(player)) {
            if (snapshot.source() == source) return snapshot;
        }
        return null;
    }

    public boolean hasSource(UUID player, PlayerLocationSource source) {
        return source(player, source) != null;
    }

    public PlayerLocationSnapshot locatorSource(UUID player) {
        PlayerLocationSnapshot best = null;
        for (PlayerLocationSnapshot snapshot : sources(player)) {
            if (!isLocator(snapshot.source())) continue;
            if (best == null || snapshot.source().priority() > best.source().priority()
                    || snapshot.observedAtMs() > best.observedAtMs()) best = snapshot;
        }
        return best;
    }

    public boolean locatorPresent(UUID player) {
        return locatorSource(player) != null;
    }

    private static boolean isLocator(PlayerLocationSource source) {
        return source == PlayerLocationSource.LOCATOR_EXACT
                || source == PlayerLocationSource.LOCATOR_APPROXIMATE
                || source == PlayerLocationSource.LOCATOR_BEARING;
    }
}
