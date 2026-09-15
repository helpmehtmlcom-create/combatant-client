/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

/** Stable on-disk event ids. Never reorder existing values. */
public enum MapHistoryEventKind {
    SOURCE_APPEARED,
    SOURCE_LOST,
    EXACT_SOURCE_ACQUIRED,
    EXACT_SOURCE_LOST,
    TELEPORT_SEGMENT_BREAK,
    SESSION_ENDED
}
