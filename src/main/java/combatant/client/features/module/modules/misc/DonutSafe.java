/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ConfigValue;
import combatant.client.config.values.EnumValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.server.DonutSafeMode;
import combatant.client.util.server.DonutSmp;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * While connected to DonutSMP, switches every module's anticheat preset to GRIM and puts the
 * previous presets back on leave or when the module is turned off.
 */
@ModuleInfo(
        id = "donutsafe",
        displayName = "DonutSafe",
        aliases = {"DonutSMP", "DonutPreset"},
        category = ModuleCategory.MISC, subcategory = ModuleSubcategory.UTILITY,
        description = "Caps every module's anticheat preset at Grim limits while you are on DonutSMP and restores your presets when you leave.")
public final class DonutSafe extends Module {

    private static final String SETTING = "anticheat_mode";

    private final BooleanValue restoreOnLeave = bool("restore_on_leave", true);

    private final DonutSafeMode state = new DonutSafeMode();

    @Override
    public void onDisable() {
        restore();
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        boolean donut = DonutSmp.isConnected();
        if (donut && !state.isActive()) {
            Map<String, String> changes = state.apply(snapshot());
            write(changes);
            if (!changes.isEmpty()) Notifier.success("DonutSMP safe mode: " + changes.size() + " modules set to Grim limits");
        } else if (!donut && state.isActive() && restoreOnLeave.get()) {
            restore();
        }
    }

    private void restore() {
        write(state.restore(snapshot()));
    }

    private static Map<String, String> snapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Module m : ModuleManager.getModules()) {
            if (m instanceof DonutSafe) continue;
            ConfigValue<?> v = m.getConfigValue(SETTING);
            if (v instanceof EnumValue<?> e) out.put(m.name(), e.getId());
        }
        return out;
    }

    private static void write(Map<String, String> changes) {
        for (Map.Entry<String, String> e : changes.entrySet()) {
            Module m = ModuleManager.get(e.getKey());
            if (m != null && m.getConfigValue(SETTING) instanceof EnumValue<?> v) v.setById(e.getValue());
        }
    }
}
