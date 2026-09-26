/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.relations.PlayerRelations;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(
        id = "visualrange",
        displayName = "VisualRange",
        aliases = {"PlayerRadar", "RangeAlert"},
        category = ModuleCategory.MISC,
        description = "Alerts you in chat and with audio when players enter or leave visual render distance."
)
public final class VisualRange extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue chat =
            bool("visualRangeChat", "chat", true);

    private final BooleanValue sound =
            bool("visualRangeSound", "sound", true);

    private final BooleanValue ignoreFriends =
            bool("visualRangeIgnoreFriends", "ignore_friends", true);

    private final BooleanValue enterAlert =
            bool("visualRangeEnterAlert", "enter_alert", true);

    private final BooleanValue leaveAlert =
            bool("visualRangeLeaveAlert", "leave_alert", true);

    private final BooleanValue showCoords =
            bool("visualRangeShowCoords", "show_coords", true);

    private final Map<UUID, String> knownPlayers = new HashMap<>();

    @Override
    public void onDisable() {
        knownPlayers.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (mc.level == null || mc.player == null) {
            knownPlayers.clear();
            return;
        }

        Set<UUID> current = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (player.equals(mc.player)) continue;

            String name = player.getName().getString();
            if (ignoreFriends.get() && PlayerRelations.get().getFriends().contains(name)) {
                continue;
            }

            UUID uuid = player.getUUID();
            current.add(uuid);

            if (!knownPlayers.containsKey(uuid)) {
                knownPlayers.put(uuid, name);

                if (enterAlert.get() && chat.get()) {
                    String msg = showCoords.get()
                            ? String.format("%s entered visual range at [%d, %d, %d]!", name, player.getBlockX(), player.getBlockY(), player.getBlockZ())
                            : String.format("%s entered visual range!", name);
                    CommandOutput.send(Component.literal(msg), CommandOutput.Tone.WARNING);
                }

                if (sound.get() && mc.player != null) {
                    mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }
            }
        }

        if (leaveAlert.get() && chat.get()) {
            for (Map.Entry<UUID, String> entry : knownPlayers.entrySet()) {
                if (!current.contains(entry.getKey())) {
                    CommandOutput.send(
                            Component.literal(String.format("%s left visual range.", entry.getValue())),
                            CommandOutput.Tone.INFO
                    );
                }
            }
        }

        knownPlayers.keySet().removeIf(uuid -> !current.contains(uuid));
    }
}
