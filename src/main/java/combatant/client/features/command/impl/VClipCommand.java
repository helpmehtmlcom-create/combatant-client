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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;

@CommandInfo(
        id = "vclip",
        aliases = {"vc", "clip"},
        usage = "@vclip <blocks>",
        descriptionKey = "command.vclip.description"
)
public final class VClipCommand implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.mc() == null || ctx.mc().player == null || ctx.mc().getConnection() == null) {
            CommandOutput.error("Player is unavailable.");
            return true;
        }

        if (ctx.args().isEmpty()) {
            CommandOutput.error("Usage: " + metadata().usage());
            return true;
        }

        double offset;
        try {
            offset = Double.parseDouble(ctx.arg(0));
        } catch (NumberFormatException e) {
            CommandOutput.error("Invalid number: " + ctx.arg(0));
            return true;
        }

        LocalPlayer player = ctx.mc().player;
        double x = player.getX();
        double y = player.getY() + offset;
        double z = player.getZ();

        ctx.mc().getConnection().send(new ServerboundMovePlayerPacket.Pos(
                x, y, z, player.onGround(), false
        ));
        player.setPos(x, y, z);

        CommandOutput.success(String.format("V-Clipped %.2f blocks.", offset));
        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 0) {
            return List.of("5", "-5", "10", "-10");
        }
        return List.of();
    }
}
