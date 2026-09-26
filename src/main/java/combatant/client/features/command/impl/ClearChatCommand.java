/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.command.impl;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.command.VanillaChatRuntimeCleanup;

@CommandInfo(
        id = "clearchat",
        aliases = {"cls", "wipe"},
        usage = "@clearchat",
        descriptionKey = "command.clearchat.description"
)
public final class ClearChatCommand implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        VanillaChatRuntimeCleanup.clearVanillaChatCache();
        CommandOutput.success("Client chat history cleared.");
        return true;
    }
}
