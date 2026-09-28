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
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;

@ModuleInfo(
        id = "autoreconnect",
        displayName = "AutoReconnect",
        aliases = {"AutoRejoin"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.autoreconnect.description")
public final class AutoReconnect extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> delaySeconds =
            num("autoReconnectDelaySeconds", "delay_seconds", 5, 1, 60);
    private final NumberValue<Integer> maxAttempts =
            num("autoReconnectMaxAttempts", "max_attempts", 3, 1, 20);

    private ServerData lastServer;
    private int countdownTicks = -1;
    private int attempts;
    private boolean reconnectIssued;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        if (!(ClientScreen.current() instanceof DisconnectedScreen)) {
            ServerData current = mc.getCurrentServer();
            if (current != null) {
                lastServer = current;
                countdownTicks = -1;
                attempts = 0;
                reconnectIssued = false;
                return;
            }
            if (!reconnectIssued) countdownTicks = -1;
            return;
        }

        reconnectIssued = false;
        if (lastServer == null || attempts >= maxAttempts.get()) return;

        if (countdownTicks < 0) {
            countdownTicks = delaySeconds.get() * 20;
            return;
        }
        if (countdownTicks-- > 0) return;

        String addressText = lastServer.ip;
        if (addressText == null || !ServerAddress.isValidAddress(addressText)) {
            countdownTicks = -1;
            attempts = maxAttempts.get();
            Notifier.warning(I18n.get("notification.autoreconnect.invalid_address"));
            return;
        }

        attempts++;
        countdownTicks = -1;
        reconnectIssued = true;
        ServerAddress address = ServerAddress.parseString(addressText);
        ConnectScreen.startConnecting(new TitleScreen(false), mc, address, lastServer, false, null);
    }

    @Override
    public void onDisable() {
        countdownTicks = -1;
        attempts = 0;
        reconnectIssued = false;
    }
}
