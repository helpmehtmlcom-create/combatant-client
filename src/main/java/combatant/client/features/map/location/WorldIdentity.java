/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

public record WorldIdentity(String dimensionKey, String providerWorldId, String realmId) {
    public WorldIdentity {
        dimensionKey = clean(dimensionKey);
        providerWorldId = clean(providerWorldId);
        realmId = clean(realmId);
    }

    public static WorldIdentity dimension(String dimensionKey) {
        return new WorldIdentity(dimensionKey, "", "");
    }

    /** Solver/storage grouping key. Provider aliases stay metadata unless configured as a realm. */
    public String stableKey() {
        return realmId.isBlank() ? dimensionKey : dimensionKey + "|" + realmId;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
