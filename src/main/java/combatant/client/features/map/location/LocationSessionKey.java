/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

/**
 * One live multiplayer location session. Generation changes on reconnect even when endpoint and
 * dimension are identical, so asynchronous solver results can never cross connection boundaries.
 */
public record LocationSessionKey(String serverKey, String worldKey, long generation) {
    public LocationSessionKey {
        serverKey = clean(serverKey);
        worldKey = clean(worldKey);
        if (serverKey.isBlank()) throw new IllegalArgumentException("serverKey");
        if (worldKey.isBlank()) throw new IllegalArgumentException("worldKey");
        if (generation <= 0L) throw new IllegalArgumentException("generation");
    }

    public String stableKey() {
        return serverKey + "|" + worldKey + "|" + generation;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
