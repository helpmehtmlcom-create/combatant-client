/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

@ModuleInfo(
        id = "autoreconnect",
        displayName = "AutoReconnect",
        aliases = {"AutoRejoin", "Reconnect"},
        category = ModuleCategory.MISC,
        description = "Automatically reconnects to the last server upon disconnection."
)
public final class AutoReconnect extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delaySeconds =
            num("autoReconnectDelaySeconds", "delay_seconds", 5, 1, 30);

    private ServerData lastServer = null;
    private int countdownTicks = -1;

    @Override
    public void onDisable() {
        countdownTicks = -1;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (mc.getCurrentServer() != null) {
            lastServer = mc.getCurrentServer();
            countdownTicks = -1;
            return;
        }

        if (ClientScreen.current() instanceof DisconnectedScreen) {
            if (lastServer == null) return;

            if (countdownTicks < 0) {
                countdownTicks = delaySeconds.get() * 20;
                return;
            }

            if (countdownTicks > 0) {
                countdownTicks--;
                return;
            }

            // Reconnect now
            countdownTicks = -1;
            ServerData server = lastServer;
            ServerAddress address = ServerAddress.parseString(server.ip);
            ConnectScreen.startConnecting(new TitleScreen(false), mc, address, server, false, null);
        } else {
            countdownTicks = -1;
        }
    }
}
