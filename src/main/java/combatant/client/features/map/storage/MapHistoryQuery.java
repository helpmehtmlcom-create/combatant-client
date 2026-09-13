/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.util.Set;
import java.util.UUID;

public record MapHistoryQuery(
        String serverKey,
        UUID targetUuid,
        long fromMs,
        long toMs,
        Set<String> worlds,
        Set<MapHistorySource> sources,
        boolean includeBearings
) {
    public MapHistoryQuery {
        serverKey = serverKey == null ? "" : serverKey.trim();
        if (targetUuid == null) throw new IllegalArgumentException("targetUuid");
        if (toMs <= 0L) toMs = Long.MAX_VALUE;
        if (fromMs < 0L) fromMs = 0L;
        if (toMs < fromMs) {
            long swap = fromMs;
            fromMs = toMs;
            toMs = swap;
        }
        worlds = worlds == null ? Set.of() : Set.copyOf(worlds);
        sources = sources == null ? Set.of() : Set.copyOf(sources);
    }

    public boolean matches(MapHistoryChunkMeta meta) {
        if (meta == null || !targetUuid.equals(meta.targetUuid())) return false;
        if (!serverKey.isBlank() && !serverKey.equals(meta.serverKey())) return false;
        if (!worlds.isEmpty() && !worlds.contains(meta.worldKey())) return false;
        if (!sources.isEmpty() && !sources.contains(meta.source())) return false;
        if (!includeBearings && meta.type() == MapHistoryRecordType.BEARING) return false;
        return meta.overlaps(fromMs, toMs);
    }
}
