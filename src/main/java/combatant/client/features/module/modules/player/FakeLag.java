/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on LiquidBounce
 * (https://github.com/CCBlueX/LiquidBounce).
 * Copyright (c) 2015-2026 CCBlueX.
 *
 * LiquidBounce portions are licensed under GPLv3-or-later.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleManager;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.util.network.FakeLagController;

/**
 * FakeLag module front-end for the shared BlinkManager-backed controller.
 */
@ModuleInfo(
        id = "fakelag",
        displayName = "FakeLag",
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.fakelag.description")
public final class FakeLag extends Module {
    private final NumberValue<Integer> latency = numCommon(
            "fakeLagLatency",
            "latency",
            CommonSettingSchemas.PLAYER_FAKELAG_DELAY_MAX,
            200,
            20,
            1000
    );

    private final EnumValue<FakeLagController.Mode> mode = enumCommon(
            "fakeLagMode",
            "mode",
            CommonSettingSchemas.PLAYER_FAKELAG_MODE,
            FakeLagController.Mode.DYNAMIC,
            FakeLagController.Mode.class
    );
    private FakeLagController.Config appliedConfig;

    @Override
    public void onEnable() {
        Blink blink = ModuleManager.get(Blink.class);
        if (blink != null && blink.isEnabled()) {
            blink.setEnabled(false);
        }
        syncControllerConfig();
        FakeLagController.INSTANCE.setEnabled(true);
    }

    @Override
    public void onDisable() {
        FakeLagController.INSTANCE.setEnabled(false);
        appliedConfig = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled()) {
            return;
        }
        syncControllerConfig();
    }

    private void syncControllerConfig() {
        FakeLagController.Config config = buildConfig();
        if (!config.equals(appliedConfig) || !config.equals(FakeLagController.INSTANCE.getConfig())) {
            FakeLagController.INSTANCE.configure(config);
            appliedConfig = config;
        }
    }

    private FakeLagController.Config buildConfig() {
        return new FakeLagController.Config(latency.get(), mode.get());
    }
}
