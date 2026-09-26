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

@CommandInfo(
        id = "rawchat",
        aliases = {"raw", "sendchat"},
        usage = "@rawchat <message>",
        descriptionKey = "command.rawchat.description"
)
public final class RawChatCommand implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.mc() == null || ctx.mc().getConnection() == null) {
            CommandOutput.error("Not connected to a server.");
            return true;
        }

        if (ctx.args().isEmpty()) {
            CommandOutput.error("Usage: " + metadata().usage());
            return true;
        }

        String msg = String.join(" ", ctx.args());
        ctx.mc().getConnection().sendChat(msg);
        return true;
    }
}
