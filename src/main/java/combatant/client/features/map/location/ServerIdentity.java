/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.location;

import java.util.Locale;

public record ServerIdentity(String endpoint, String profileId, String realmId) {
    public ServerIdentity {
        endpoint = normalize(endpoint);
        profileId = clean(profileId);
        realmId = clean(realmId);
    }

    public static ServerIdentity endpoint(String endpoint) {
        return new ServerIdentity(endpoint, "", "");
    }

    public String stableKey() {
        if (profileId.isBlank() && realmId.isBlank()) return endpoint;
        return endpoint + "|" + profileId + "|" + realmId;
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
