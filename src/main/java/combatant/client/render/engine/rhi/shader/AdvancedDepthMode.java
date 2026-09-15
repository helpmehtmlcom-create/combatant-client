/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Native graphics depth policy. Minecraft's primary world depth is reversed-Z. */
public enum AdvancedDepthMode {
    DISABLED,
    READ_ONLY_LEQUAL,
    READ_WRITE_LEQUAL,
    READ_ONLY_GREATER_EQUAL,
    READ_WRITE_GREATER_EQUAL;

    public boolean writesDepth() {
        return this == READ_WRITE_LEQUAL || this == READ_WRITE_GREATER_EQUAL;
    }

    public boolean reversedZ() {
        return this == READ_ONLY_GREATER_EQUAL || this == READ_WRITE_GREATER_EQUAL;
    }
}
