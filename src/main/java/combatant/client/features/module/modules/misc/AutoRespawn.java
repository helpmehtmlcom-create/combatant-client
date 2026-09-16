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
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;

@ModuleInfo(
        id = "autorespawn",
        displayName = "AutoRespawn",
        category = ModuleCategory.MISC,
        description = "Automatically respawns immediately upon dying to bypass the death screen."
)
public class AutoRespawn extends Module {

    private final NumberValue<Integer> delay = num("delay", 0, 0, 40);
    private final BooleanValue deathCoordinates = bool("deathCoordinates", "death_coordinates", true);
    private final BooleanValue antiDeathScreenLock = bool("antiDeathScreenLock", "anti_death_screen_lock", true);

    private final Minecraft mc = Minecraft.getInstance();
    private int timer = 0;
    private boolean loggedDeathCoordinates = false;

    public NumberValue<Integer> getDelay() {
        return delay;
    }

    public BooleanValue getDeathCoordinates() {
        return deathCoordinates;
    }

    public BooleanValue getAntiDeathScreenLock() {
        return antiDeathScreenLock;
    }

    @Override
    public List<String> getLegacyValueNames(String currentValueName) {
        if ("deathCoordinates".equals(currentValueName) || "death_coordinates".equals(currentValueName)) {
            return List.of("death_coordinates", "deathCoordinates");
        }
        if ("antiDeathScreenLock".equals(currentValueName) || "anti_death_screen_lock".equals(currentValueName)) {
            return List.of("anti_death_screen_lock", "antiDeathScreenLock");
        }
        return super.getLegacyValueNames(currentValueName);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetTimer();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetTimer();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        handleTick();
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) {
            return;
        }
        handleTick();
    }

    private void handleTick() {
        if (mc.player == null) {
            resetTimer();
            return;
        }

        Screen currentScreen = ClientScreen.current(mc);
        boolean deathScreenOpen = currentScreen instanceof DeathScreen;
        boolean dead = mc.player.isDeadOrDying() || mc.player.getHealth() <= 0.0f || deathScreenOpen;

        if (!dead) {
            resetTimer();
            return;
        }

        timer++;
        if (timer >= delay.get()) {
            if (deathCoordinates.get() && !loggedDeathCoordinates) {
                logDeathCoordinates();
                loggedDeathCoordinates = true;
            }

            respawn();

            if (!antiDeathScreenLock.get()) {
                timer = 0;
            } else {
                // If antiDeathScreenLock is enabled and screen is still locked on DeathScreen,
                // keep timer at delay threshold so subsequent ticks continue attempting to clear it.
                if (!(ClientScreen.current(mc) instanceof DeathScreen)) {
                    timer = 0;
                }
            }
        }
    }

    private void respawn() {
        if (mc.player != null) {
            mc.player.respawn();
        }
        ClientScreen.show(mc, null);
        if (mc.gui != null) {
            mc.gui.setScreen(null);
        }
    }

    private void logDeathCoordinates() {
        if (mc.player == null) {
            return;
        }

        int x = mc.player.getBlockX();
        int y = mc.player.getBlockY();
        int z = mc.player.getBlockZ();
        String dimension = resolveDimension();

        String message = String.format(Locale.ROOT, "[AutoRespawn] Died at %d, %d, %d in %s", x, y, z, dimension);

        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
        } else {
            CommandOutput.send(Component.literal(message));
        }
        DebugLog.info(message);
    }

    private String resolveDimension() {
        if (mc.level == null) {
            return "unknown";
        }
        if (mc.level.dimension() == Level.OVERWORLD) {
            return "overworld";
        }
        if (mc.level.dimension() == Level.NETHER) {
            return "nether";
        }
        if (mc.level.dimension() == Level.END) {
            return "end";
        }
        return mc.level.dimension().identifier().getPath();
    }

    private void resetTimer() {
        timer = 0;
        loggedDeathCoordinates = false;
    }
}
