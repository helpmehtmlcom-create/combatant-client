/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PlayerJumpEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;

@ModuleInfo(
        id = "highjump",
        displayName = "HighJump",
        aliases = {"JumpBoost", "SuperJump"},
        category = ModuleCategory.MOVEMENT,
        description = "Increases your jump height by multiplying or overriding jump velocity."
)
public final class HighJump extends Module {

    private final EnumValue<Mode> mode =
            enumSetting("highJumpMode", "mode", Mode.MULTIPLIER, Mode.values());

    private final NumberValue<Float> multiplier =
            visibleWhen(num("highJumpMultiplier", "multiplier", 1.5f, 1.0f, 5.0f), () -> mode.get() == Mode.MULTIPLIER);

    private final NumberValue<Float> customMotion =
            visibleWhen(num("highJumpCustomMotion", "custom_motion", 0.75f, 0.42f, 3.0f), () -> mode.get() == Mode.CUSTOM);

    @EventHandler
    public void onPlayerJump(PlayerJumpEvent event) {
        if (!isEnabled()) return;

        switch (mode.get()) {
            case MULTIPLIER -> event.setMotion(event.getMotion() * multiplier.get());
            case CUSTOM -> event.setMotion(customMotion.get());
        }
    }

    public enum Mode {
        MULTIPLIER,
        CUSTOM
    }
}
