/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

@ModuleInfo(
        id = "autorespawn",
        displayName = "AutoRespawn",
        aliases = {"InstantRespawn", "FastRespawn"},
        category = ModuleCategory.MISC,
        description = "Automatically respawns upon death and optionally logs death coordinates to chat."
)
public final class AutoRespawn extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delayTicks =
            num("autoRespawnDelay", "delay_ticks", 0, 0, 60);

    private final BooleanValue logCoordinates =
            bool("autoRespawnLogCoords", "log_coordinates", true);

    private int waitedTicks = 0;
    private boolean reportedDeath = false;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (ClientScreen.current() instanceof DeathScreen) {
            LocalPlayer player = mc.player;
            if (player == null) return;
            if (!reportedDeath && logCoordinates.get()) {
                BlockPos pos = player.blockPosition();
                CommandOutput.send(
                        Component.literal(String.format("Died at: X: %d, Y: %d, Z: %d", pos.getX(), pos.getY(), pos.getZ())),
                        CommandOutput.Tone.WARNING
                );
                reportedDeath = true;
            }

            if (waitedTicks < delayTicks.get()) {
                waitedTicks++;
                return;
            }

            player.respawn();
            ClientScreen.show(null);
            waitedTicks = 0;
            reportedDeath = false;
        } else {
            waitedTicks = 0;
            reportedDeath = false;
        }
    }

    @Override
    public void onDisable() {
        waitedTicks = 0;
        reportedDeath = false;
    }
}
