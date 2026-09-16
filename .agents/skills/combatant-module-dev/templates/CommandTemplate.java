package combatant.client.features.command.impl;

import combatant.client.features.command.ClientCommand;
import combatant.client.features.command.CommandContext;
import combatant.client.features.command.CommandInfo;
import java.util.List;

/**
 * Standard Production Client Command Template for Combatant Client.
 *
 * <p>Requirements:
 * 1. Must be annotated with {@link CommandInfo}.
 * 2. Implements {@link ClientCommand}.
 * 3. Handles command execution via {@code execute(CommandContext ctx)}.
 * 4. Provides autocompletions via {@code suggest(CommandContext ctx, int argIndex, String token)}.
 */
@CommandInfo(
    id = "templatecmd",
    aliases = {"tcmd", "tmplcmd"},
    usage = "@templatecmd <get|set|reset> [value]",
    description = "Demonstrates production-ready client command architecture."
)
public final class CommandTemplate implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        if (ctx.argCount() == 0) {
            ctx.output().info("Usage: " + metadata().usage());
            return true;
        }

        String subCommand = ctx.arg(0).toLowerCase();
        switch (subCommand) {
            case "get" -> {
                ctx.output().info("Parameter is currently: active");
                return true;
            }
            case "set" -> {
                if (ctx.argCount() < 2) {
                    ctx.output().error("Missing value. Usage: @templatecmd set <value>");
                    return true;
                }
                String value = ctx.arg(1);
                ctx.output().success("Parameter set to: " + value);
                return true;
            }
            case "reset" -> {
                ctx.output().success("Parameter reset to default.");
                return true;
            }
            default -> {
                ctx.output().error("Unknown action '" + subCommand + "'. Use @help " + metadata().id());
                return true;
            }
        }
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        if (argIndex == 0) {
            return List.of("get", "set", "reset");
        }
        return List.of();
    }
}
