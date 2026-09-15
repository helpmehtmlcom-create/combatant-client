/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.map;

import java.util.Objects;
import java.util.UUID;

/** Player identity marker rendered as a head + name instead of a generic point marker. */
public record MapPlayerMarker(String id,
                              double worldX,
                              double worldZ,
                              UUID playerUuid,
                              String playerName,
                              String statusLabel,
                              int accentArgb,
                              String sourceGlyph,
                              float sizePixels,
                              float alpha,
                              int priority) {
    public MapPlayerMarker {
        Objects.requireNonNull(id, "id");
        playerName = playerName == null ? "" : playerName.trim();
        statusLabel = statusLabel == null ? "" : statusLabel.trim();
        sourceGlyph = sourceGlyph == null ? "" : sourceGlyph.trim();
        if (!Double.isFinite(worldX) || !Double.isFinite(worldZ)
                || !Float.isFinite(sizePixels) || sizePixels <= 0.0f
                || !Float.isFinite(alpha)) {
            throw new IllegalArgumentException("Invalid player marker geometry.");
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
    }
}
