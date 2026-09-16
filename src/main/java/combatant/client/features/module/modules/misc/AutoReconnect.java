/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(
        id = "autoreconnect",
        displayName = "AutoReconnect",
        category = ModuleCategory.MISC,
        aliases = {"reconnect", "queueconnect"},
        description = "Automatically attempts to reconnect to the server after being disconnected or kicked."
)
public class AutoReconnect extends Module {

    private final NumberValue<Integer> delay = num("delay", 5, 1, 60);
    private final BooleanValue cancelOnEsc = bool("cancelOnEsc", "cancel_on_esc", true);

    private final Minecraft mc = Minecraft.getInstance();

    private ServerData lastServer;
    private long disconnectTimestamp = -1L;
    private boolean cancelled = false;
    private Screen lastHandledScreen = null;
    private Component originalButtonText = null;
    private Button targetButton = null;

    public NumberValue<Integer> getDelay() {
        return delay;
    }

    public BooleanValue getCancelOnEsc() {
        return cancelOnEsc;
    }

    public ServerData getLastServer() {
        return lastServer;
    }

    public void setLastServer(ServerData lastServer) {
        this.lastServer = lastServer;
    }

    @Override
    public void onEnable() {
        resetCountdown();
    }

    @Override
    public void onDisable() {
        restoreButtonMessage();
        resetCountdown();
    }

    @Override
    public void onTick() {
        updateLastServer();
        handleReconnectLogic();
    }

    @Override
    public void onFrame(float frameDeltaTicks) {
        updateLastServer();
        handleReconnectLogic();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        updateLastServer();
        handleReconnectLogic();
    }

    private void updateLastServer() {
        if (mc.getCurrentServer() != null) {
            this.lastServer = mc.getCurrentServer();
        }
    }

    private void handleReconnectLogic() {
        if (!isEnabled()) {
            return;
        }
        Screen currentScreen = ClientScreen.current(mc);
        if (!(currentScreen instanceof DisconnectedScreen disconnectedScreen)) {
            if (lastHandledScreen != null) {
                resetCountdown();
            }
            return;
        }

        if (lastServer == null) {
            return;
        }

        // Screen just changed to DisconnectedScreen
        if (lastHandledScreen != disconnectedScreen) {
            lastHandledScreen = disconnectedScreen;
            disconnectTimestamp = System.currentTimeMillis();
            cancelled = false;
            originalButtonText = null;
            targetButton = null;
        }

        // Check cancel on ESC
        if (cancelOnEsc.get() && !cancelled) {
            long windowHandle = mc.getWindow().handle();
            if (windowHandle != 0L && GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS) {
                cancelled = true;
                restoreButtonMessage();
                return;
            }
        }

        if (cancelled) {
            return;
        }

        long delayMs = delay.get() * 1000L;
        long elapsed = System.currentTimeMillis() - disconnectTimestamp;
        long remainingMs = Math.max(0L, delayMs - elapsed);
        double secondsLeft = remainingMs / 1000.0;

        // Locate or update button
        updateReconnectButton(disconnectedScreen, secondsLeft);

        // Timer expired -> reconnect
        if (remainingMs <= 0L) {
            reconnect();
        }
    }

    private void updateReconnectButton(DisconnectedScreen screen, double secondsLeft) {
        if (targetButton == null || !screen.children().contains(targetButton)) {
            targetButton = null;
            for (GuiEventListener child : screen.children()) {
                if (child instanceof Button button) {
                    targetButton = button;
                    if (originalButtonText == null) {
                        originalButtonText = button.getMessage();
                    }
                    break;
                }
            }
        }

        if (targetButton != null) {
            String formattedSeconds = String.format(java.util.Locale.ROOT, "%.1f", secondsLeft);
            targetButton.setMessage(Component.literal("Reconnecting in " + formattedSeconds + "s..."));
        }
    }

    private void restoreButtonMessage() {
        if (targetButton != null && originalButtonText != null) {
            targetButton.setMessage(originalButtonText);
        }
    }

    private void reconnect() {
        if (lastServer == null) {
            return;
        }

        ServerData target = this.lastServer;
        resetCountdown();

        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        ServerAddress serverAddress = ServerAddress.parseString(target.ip);
        ConnectScreen.startConnecting(parent, mc, serverAddress, target, false, null);
    }

    private void resetCountdown() {
        restoreButtonMessage();
        disconnectTimestamp = -1L;
        cancelled = false;
        lastHandledScreen = null;
        originalButtonText = null;
        targetButton = null;
    }
}
