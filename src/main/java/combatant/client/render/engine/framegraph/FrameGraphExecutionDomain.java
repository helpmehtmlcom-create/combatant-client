/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

/** Logical execution domain used when lowering resource hazards to backend barriers. */
public enum FrameGraphExecutionDomain {
    GRAPHICS,
    COMPUTE,
    TRANSFER
}
