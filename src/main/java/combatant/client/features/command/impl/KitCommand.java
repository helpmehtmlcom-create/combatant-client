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
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.player.AutoKit;
import combatant.client.features.module.modules.player.InventorySorter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@CommandInfo(
        id = "kit",
        aliases = {"kits", "invsort", "sort"},
        usage = "@kit <save|load|sort|equip|list|delete> [name]",
        descriptionKey = "command.kit.description"
)
public final class KitCommand implements ClientCommand {

    @Override
    public boolean execute(CommandContext ctx) {
        String sub = ctx.arg(0);
        if (sub == null || sub.isEmpty()) {
            CommandOutput.warning("Usage: " + metadata().usage());
            return true;
        }

        InventorySorter sorter = Modules.get(InventorySorter.class);
        AutoKit autoKit = Modules.get(AutoKit.class);

        switch (sub.toLowerCase(Locale.ROOT)) {
            case "save" -> {
                if (sorter == null) {
                    CommandOutput.error("InventorySorter module not loaded.");
                    return true;
                }
                String name = ctx.arg(1);
                if (name == null || name.trim().isEmpty()) {
                    CommandOutput.error("Usage: @kit save <name>");
                    return true;
                }
                sorter.saveCurrentInventory(name.trim());
                CommandOutput.success("Saved kit: " + name.trim());
            }
            case "load" -> {
                if (sorter == null) {
                    CommandOutput.error("InventorySorter module not loaded.");
                    return true;
                }
                String name = ctx.arg(1);
                if (name == null || name.trim().isEmpty()) {
                    CommandOutput.error("Usage: @kit load <name>");
                    return true;
                }
                if (sorter.loadKit(name.trim())) {
                    CommandOutput.success("Active kit set to: " + name.trim());
                    if (autoKit != null) {
                        autoKit.setActiveKitName(name.trim());
                    }
                } else {
                    CommandOutput.error("Kit not found: " + name.trim());
                }
            }
            case "sort" -> {
                if (sorter == null) {
                    CommandOutput.error("InventorySorter module not loaded.");
                    return true;
                }
                String name = ctx.arg(1);
                if (name != null && !name.trim().isEmpty()) {
                    sorter.loadKit(name.trim());
                }
                sorter.sort();
            }
            case "equip" -> {
                if (autoKit == null) {
                    CommandOutput.error("AutoKit module not loaded.");
                    return true;
                }
                String name = ctx.arg(1);
                if (name != null && !name.trim().isEmpty()) {
                    autoKit.setActiveKitName(name.trim());
                }
                autoKit.equipKit();
            }
            case "list" -> {
                if (sorter == null) {
                    CommandOutput.error("InventorySorter module not loaded.");
                    return true;
                }
                List<String> names = sorter.getKitNames();
                if (names.isEmpty()) {
                    CommandOutput.send("No saved kits found.");
                } else {
                    CommandOutput.send("Saved kits (" + names.size() + "): " + String.join(", ", names)
                            + " [Active: " + sorter.getActiveKit() + "]");
                }
            }
            case "delete", "del", "remove" -> {
                if (sorter == null) {
                    CommandOutput.error("InventorySorter module not loaded.");
                    return true;
                }
                String name = ctx.arg(1);
                if (name == null || name.trim().isEmpty()) {
                    CommandOutput.error("Usage: @kit delete <name>");
                    return true;
                }
                if (sorter.deleteKit(name.trim())) {
                    CommandOutput.success("Deleted kit: " + name.trim());
                } else {
                    CommandOutput.error("Kit not found: " + name.trim());
                }
            }
            default -> CommandOutput.warning("Unknown subcommand: " + sub + ". Usage: " + metadata().usage());
        }

        return true;
    }

    @Override
    public List<String> suggest(CommandContext ctx, int argIndex, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        if (argIndex == 1) {
            return List.of("save", "load", "sort", "equip", "list", "delete").stream()
                    .filter(s -> s.startsWith(lower))
                    .toList();
        }
        if (argIndex == 2) {
            InventorySorter sorter = Modules.get(InventorySorter.class);
            if (sorter != null) {
                return sorter.getKitNames().stream()
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                        .toList();
            }
        }
        return List.of();
    }
}
