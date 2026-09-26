/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

public record IrisAaIntegrationState(
        IrisAaOwner owner,
        String requestedMode,
        boolean shaderpackActive,
        boolean adapterAvailable,
        boolean patchApplied,
        boolean nativeTemporalBypass,
        long integrationEpoch,
        String reason
) {
    public IrisAaIntegrationState {
        owner = owner == null ? IrisAaOwner.NONE : owner;
        requestedMode = requestedMode == null ? "off" : requestedMode;
        reason = reason == null ? "" : reason;
    }

    public String shortLine() {
        return "aa owner=" + owner
                + " requested=" + requestedMode
                + " nativeBypass=" + (nativeTemporalBypass ? "yes" : "no")
                + " patch=" + (patchApplied ? "applied" : "unavailable")
                + (reason.isBlank() ? "" : " reason=" + reason);
    }
}
