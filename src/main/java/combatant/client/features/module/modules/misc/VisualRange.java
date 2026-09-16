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
import combatant.client.features.module.Notifier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ModuleInfo(
        id = "visualrange",
        displayName = "VisualRange",
        category = ModuleCategory.MISC,
        aliases = {"playerdetector", "radaralert"},
        description = "Alerts you in chat or notifications when another player enters your visual render distance."
)
public class VisualRange extends Module {

    private static final UUID FAKE_PLAYER_UUID = UUID.fromString("66123666-6666-6666-6666-666666666600");

    private final BooleanValue enter = bool("enter", true);
    private final BooleanValue leave = bool("leave", true);
    private final BooleanValue coordinates = bool("coordinates", true);
    private final BooleanValue sound = bool("sound", true);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<UUID> knownPlayers = new HashSet<>();
    private final Map<UUID, String> knownPlayerNames = new HashMap<>();

    public BooleanValue getEnter() {
        return enter;
    }

    public BooleanValue getLeave() {
        return leave;
    }

    public BooleanValue getCoordinates() {
        return coordinates;
    }

    public BooleanValue getSound() {
        return sound;
    }

    public Set<UUID> getKnownPlayers() {
        return knownPlayers;
    }

    @Override
    public void onEnable() {
        knownPlayers.clear();
        knownPlayerNames.clear();

        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                if (isExcluded(player)) continue;
                knownPlayers.add(player.getUUID());
                knownPlayerNames.put(player.getUUID(), player.getName().getString());
            }
        }
    }

    @Override
    public void onDisable() {
        knownPlayers.clear();
        knownPlayerNames.clear();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            return;
        }

        Set<UUID> currentPlayers = new HashSet<>();

        for (Player player : mc.level.players()) {
            if (isExcluded(player)) continue;

            UUID uuid = player.getUUID();
            String name = player.getName().getString();
            currentPlayers.add(uuid);
            knownPlayerNames.put(uuid, name);

            if (!knownPlayers.contains(uuid)) {
                knownPlayers.add(uuid);

                if (enter.get()) {
                    String message;
                    if (coordinates.get()) {
                        message = String.format(
                                Locale.ROOT,
                                "[VisualRange] %s entered visual range at (%d, %d, %d)!",
                                name,
                                player.getBlockX(),
                                player.getBlockY(),
                                player.getBlockZ()
                        );
                    } else {
                        message = String.format(Locale.ROOT, "[VisualRange] %s entered visual range!", name);
                    }

                    outputMessage(message, true);
                }
            }
        }

        // Check for players that left visual range
        Set<UUID> toRemove = null;
        for (UUID uuid : knownPlayers) {
            if (!currentPlayers.contains(uuid)) {
                if (toRemove == null) {
                    toRemove = new HashSet<>();
                }
                toRemove.add(uuid);
            }
        }

        if (toRemove != null) {
            for (UUID uuid : toRemove) {
                knownPlayers.remove(uuid);
                String name = knownPlayerNames.remove(uuid);
                if (name == null || name.isBlank()) {
                    name = uuid.toString();
                }

                if (leave.get()) {
                    String message = String.format(Locale.ROOT, "[VisualRange] %s left visual range!", name);
                    outputMessage(message, false);
                }
            }
        }
    }

    private boolean isExcluded(Player player) {
        if (player == null) return true;
        if (player == mc.player) return true;
        if (mc.player != null && player.getUUID().equals(mc.player.getUUID())) return true;
        if (player.getId() < 0) return true;
        if (FAKE_PLAYER_UUID.equals(player.getUUID())) return true;
        String name = player.getName().getString();
        if (name != null && name.endsWith("_fake")) return true;
        return false;
    }

    private void outputMessage(String message, boolean isEnter) {
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        } else {
            CommandOutput.send(Component.literal(message));
        }

        if (isEnter) {
            Notifier.warning(message);
        } else {
            Notifier.info(message);
        }

        if (sound.get()) {
            playPing();
        }
    }

    private void playPing() {
        if (mc.player != null && mc.level != null) {
            mc.level.playSound(
                    null,
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.0f
            );
        }
    }
}
