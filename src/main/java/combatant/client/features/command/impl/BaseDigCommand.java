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
import combatant.client.features.module.modules.player.AutoBaseDig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

@CommandInfo(
        id = "basedig",
        aliases = {"bdig", "autodig"},
        usage = "@basedig [pos1|pos2|start|stop]",
        descriptionKey = "command.basedig.description"
)
public final class BaseDigCommand implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        String sub = ctx.arg(0);
        AutoBaseDig module = AutoBaseDig.getInstance();
        if (module == null) {
            CommandOutput.error("AutoBaseDig module is not initialized.");
            return true;
        }

        if (sub == null || sub.isBlank() || sub.equalsIgnoreCase("help")) {
            CommandOutput.info("§dAutoBaseDig Commands:");
            CommandOutput.send("§7@basedig pos1 §f- Set excavation Pos 1 to your position");
            CommandOutput.send("§7@basedig pos2 §f- Set excavation Pos 2 to your position");
            CommandOutput.send("§7@basedig start §f- Start area excavation");
            CommandOutput.send("§7@basedig stop §f- Stop area excavation");
            return true;
        }

        BlockPos playerPos = mc.player.blockPosition();

        switch (sub.toLowerCase()) {
            case "pos1", "1" -> {
                module.setPos1(playerPos);
                CommandOutput.info("§d[AutoBaseDig] §aPos 1 set to: §f" + playerPos.toShortString());
                return true;
            }
            case "pos2", "2" -> {
                module.setPos2(playerPos);
                CommandOutput.info("§d[AutoBaseDig] §aPos 2 set to: §f" + playerPos.toShortString());
                return true;
            }
            case "start", "on" -> {
                module.setEnabled(true);
                CommandOutput.info("§d[AutoBaseDig] §aExcavation started!");
                return true;
            }
            case "stop", "off" -> {
                module.setEnabled(false);
                CommandOutput.info("§d[AutoBaseDig] §cExcavation stopped.");
                return true;
            }
            default -> {
                CommandOutput.error("Unknown argument. Usage: @basedig [pos1|pos2|start|stop]");
                return true;
            }
        }
    }
}
