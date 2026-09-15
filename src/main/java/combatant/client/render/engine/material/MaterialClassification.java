/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

/** Producer-side semantic classification independent from texture maps and scalar BRDF values. */
public record MaterialClassification(
        MaterialDomain domain,
        int traitMask,
        MaterialSubmissionRoute route,
        MaterialResolutionSource source
) {
    public static final MaterialClassification UNKNOWN = new MaterialClassification(
            MaterialDomain.UNKNOWN, 0, MaterialSubmissionRoute.COMPATIBILITY, MaterialResolutionSource.UNKNOWN
    );

    public MaterialClassification {
        if (domain == null) domain = MaterialDomain.UNKNOWN;
        if (route == null) route = MaterialSubmissionRoute.COMPATIBILITY;
        if (source == null) source = MaterialResolutionSource.UNKNOWN;
    }

    public boolean has(MaterialTrait trait) {
        return trait != null && (traitMask & trait.bit()) != 0;
    }

    public MaterialClassification withDomain(MaterialDomain newDomain) {
        return new MaterialClassification(newDomain, traitMask, route, source);
    }
}
