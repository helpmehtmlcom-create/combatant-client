/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

/**
 * Notifies the player when weather changes on DonutSMP (thunderstorms / rain enable riptide tridents).
 * Ported and enhanced from 67Client's WeatherNotifierModule.
 */
@ModuleInfo(
        id = "weathernotifier",
        displayName = "WeatherNotifier",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.DONUTSMP,
        description = "Alerts when rain or thunderstorm begins on DonutSMP for trident riptide escapes."
)
public class WeatherNotifier extends Module {

    public enum OutputMode {
        CHAT,
        ACTION_BAR
    }

    private final BooleanValue thunder =
            bool("weather_thunder", "thunder", true);
    private final BooleanValue rain =
            bool("weather_rain", "rain", true);
    private final BooleanValue sound =
            bool("weather_sound", "sound", true);
    private final EnumValue<OutputMode> output =
            enumSetting("weather_output", "output", OutputMode.CHAT, OutputMode.values());

    private Boolean lastRaining = null;
    private Boolean lastThundering = null;

    @Override
    public void onEnable() {
        lastRaining = null;
        lastThundering = null;
    }

    @Override
    public void onDisable() {
        lastRaining = null;
        lastThundering = null;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null) {
            return;
        }

        boolean raining = level.isRaining();
        boolean thundering = level.isThundering();

        if (lastRaining != null && lastThundering != null) {
            if (thundering != lastThundering) {
                lastThundering = thundering;
                if (thunder.get()) {
                    if (thundering) {
                        notify(mc, "§6⚡ Thunderstorm rolling in! §7(Trident Riptide enabled)", true);
                    } else {
                        notify(mc, "§7⚡ Thunderstorm cleared.", false);
                    }
                }
            }

            if (raining != lastRaining) {
                lastRaining = raining;
                if (rain.get() && !thundering) {
                    if (raining) {
                        notify(mc, "§9🌧 Rain starting to fall.", true);
                    } else {
                        notify(mc, "§7🌧 Skies cleared, rain stopped.", false);
                    }
                }
            }
        } else {
            lastRaining = raining;
            lastThundering = thundering;
        }
    }

    private void notify(Minecraft mc, String message, boolean active) {
        Component text = Component.literal("§d[Weather] " + message);
        if (output.get() == OutputMode.ACTION_BAR) {
            if (mc.gui != null && mc.gui.hud != null) {
                mc.gui.hud.setOverlayMessage(text, false);
            }
        } else if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(text);
        }

        if (sound.get()) {
            float pitch = active ? 1.25f : 0.85f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.75f));
        }
    }
}
