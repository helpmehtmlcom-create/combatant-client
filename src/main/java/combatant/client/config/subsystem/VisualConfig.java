/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.subsystem;

import combatant.client.config.SettingDef;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.render.iris.IrisAaIntegration;
import combatant.client.render.iris.IrisAaOwner;

import java.util.List;

@ConfigSubsystem(value = "visual", legacyNames = "mainconfig", settingOwner = "main_config")
public final class VisualConfig extends SubsystemConfig {
    public static final VisualConfig INSTANCE = new VisualConfig();

    private final BooleanValue combatantMainMenu = bool("combatantMainMenu", true);
    private final ModeValue menuBackground = mode("menuBackground", "png", "png", "aurora", "waves");
    private final BooleanValue menuClockShowSeconds = bool("menuClockShowSeconds", false);
    /** Canonical world anti-aliasing owner. MSAA and TAA are mutually exclusive. */
    private final ModeValue antialiasing3d = mode("antialiasing3d", "off", "off", "msaa", "taa");
    // Legacy source key is retained only to migrate old mainconfig values once.
    private final ModeValue legacyMsaa3d = mode("msaa3d", "off", "off", "2x", "4x");
    private final ModeValue msaa3dSamples = mode("msaa3dSamples", "4x", "2x", "4x");
    private final BooleanValue taaFxaa = bool("taaFxaa", true);
    private final BooleanValue taaSharpen = bool("taaSharpen", true);
    private final NumberValue<Double> taaSharpeningIntensity =
            number("taaSharpeningIntensity", 0.50, 0.0, 1.0);
    private final BooleanValue clickGuiModulesHints = bool("clickGuiModulesHints", true);
    private final BooleanValue clickGuiHudEditorHints = bool("clickGuiHudEditorHints", true);

    private VisualConfig() {
        loadConfig();
    }

    public static VisualConfig get() {
        return INSTANCE;
    }

    public boolean isCombatantMainMenuEnabled() {
        return combatantMainMenu.get();
    }

    public String getMenuBackgroundMode() {
        String mode = menuBackground.get();
        return mode != null ? mode : "png";
    }

    public boolean isMenuClockShowSeconds() {
        return menuClockShowSeconds.get();
    }

    public String getAntialiasing3dMode() {
        String value = antialiasing3d.get();
        return value == null ? "off" : value;
    }

    public boolean isMsaa3dSelected() {
        return "msaa".equalsIgnoreCase(getAntialiasing3dMode());
    }

    public boolean isTaaSelected() {
        return "taa".equalsIgnoreCase(getAntialiasing3dMode());
    }

    public boolean isTaaRuntimeActive() {
        return IrisAaIntegration.resolve(getAntialiasing3dMode()).owner() == IrisAaOwner.COMBATANT_TAA;
    }

    public int getMsaa3dSamples() {
        var ownership = IrisAaIntegration.resolve(getAntialiasing3dMode());
        if (ownership.owner() != IrisAaOwner.COMBATANT_MSAA) return 0;
        // The vanilla/Sodium wrapper must not wrap Iris' main target. A shaderpack adapter
        // allocates and resolves multisample gbuffer attachments at Iris' own world boundary.
        if (ownership.shaderpackActive()) return 0;
        return getConfiguredMsaa3dSamples();
    }

    public int getConfiguredMsaa3dSamples() {
        String value = msaa3dSamples.get();
        if (value == null) return 4;
        if (value.equalsIgnoreCase("2x")) return 2;
        return 4;
    }

    public boolean isTaaFxaaEnabled() {
        return taaFxaa.get();
    }

    public boolean isTaaSharpenEnabled() {
        return taaSharpen.get();
    }

    public float getTaaSharpeningIntensity() {
        return taaSharpeningIntensity.get().floatValue();
    }

    public boolean isClickGuiModulesHintsEnabled() {
        return clickGuiModulesHints.get();
    }

    public boolean isClickGuiHintsEnabled() {
        return clickGuiModulesHints.get();
    }

    public void setClickGuiModulesHintsEnabled(boolean enabled) {
        clickGuiModulesHints.set(enabled);
        saveConfig();
    }

    public void setClickGuiHintsEnabled(boolean enabled) {
        setClickGuiModulesHintsEnabled(enabled);
    }

    public boolean isClickGuiHudEditorHintsEnabled() {
        return clickGuiHudEditorHints.get();
    }

    public void setClickGuiHudEditorHintsEnabled(boolean enabled) {
        clickGuiHudEditorHints.set(enabled);
        saveConfig();
    }

    @Override
    protected void afterLoad() {
        String legacy = legacyMsaa3d.get();
        if ("off".equalsIgnoreCase(getAntialiasing3dMode()) && legacy != null && !"off".equalsIgnoreCase(legacy)) {
            antialiasing3d.set("msaa");
            msaa3dSamples.set("2x".equalsIgnoreCase(legacy) ? "2x" : "4x");
        }
        // Old key remains serialized as OFF only for backward-compatible config parsing.
        legacyMsaa3d.set("off");
    }

    @Override
    public List<SettingDef> getSettingDefs() {
        return settings(
                SettingDef.mode(antialiasing3d),
                SettingDef.mode(msaa3dSamples).visibleWhen(this::isMsaa3dSelected),
                SettingDef.bool(taaFxaa).visibleWhen(this::isTaaSelected),
                SettingDef.bool(taaSharpen).visibleWhen(this::isTaaSelected),
                SettingDef.number(taaSharpeningIntensity)
                        .visibleWhen(() -> isTaaSelected() && taaSharpen.get()),
                SettingDef.bool(combatantMainMenu),
                SettingDef.mode(menuBackground).visibleWhen(combatantMainMenu::get),
                SettingDef.bool(menuClockShowSeconds)
        );
    }

}
