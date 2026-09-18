/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;

import java.awt.Color;

/**
 * Customizes the enchantment glint color, speed, and intensity on armor and held items.
 * Ported from 67Client's CustomGlintModule.
 */
@ModuleInfo(
        id = "customglint",
        displayName = "CustomGlint",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.NORMAL,
        description = "Recolors or restyles enchantment glint with custom colors and rainbow effects."
)
public class CustomGlint extends Module {

    public enum Mode {
        SOLID,
        RAINBOW
    }

    private final EnumValue<Mode> mode =
            enumSetting("glint_mode", "mode", Mode.SOLID, Mode.values());
    private final RGBAColorValue color =
            color("glint_color", "#AA00FFFF");
    private final NumberValue<Double> speed =
            num("glint_speed", "speed", 1.0, 0.1, 5.0);
    private final NumberValue<Double> strength =
            num("glint_strength", "strength", 100.0, 10.0, 200.0);

    public int getGlintColor() {
        if (!isEnabled()) {
            return -1;
        }

        int baseRgb;
        if (mode.get() == Mode.RAINBOW) {
            double seconds = (System.currentTimeMillis() % 1000000L) / 1000.0;
            float hue = (float) ((seconds * 0.2 * speed.get()) % 1.0);
            baseRgb = Color.HSBtoRGB(hue, 0.85f, 1.0f);
        } else {
            baseRgb = color.getArgb();
        }

        float factor = strength.get().floatValue() / 100.0f;
        int r = Math.min(255, Math.round(((baseRgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((baseRgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((baseRgb & 0xFF) * factor));
        int a = (baseRgb >> 24) & 0xFF;

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public float getStrengthMultiplier() {
        return strength.get().floatValue() / 100.0f;
    }
}
