/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.command;

/**
 * Normalized UI command root.
 * <p>
 * Commands emitted by Renderer2D into the ordered UI compiler.
 */
public interface UiCommand {
    UiCommandKind kind();
}
