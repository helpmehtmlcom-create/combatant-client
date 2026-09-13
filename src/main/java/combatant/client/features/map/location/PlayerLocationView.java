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
}
