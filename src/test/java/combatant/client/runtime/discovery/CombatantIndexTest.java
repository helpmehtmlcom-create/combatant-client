/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.discovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatantIndexTest {
    @Test
    void containsTopLevelAndNestedBuiltinComponents() {
        assertTrue(CombatantIndex.classNames(CombatantIndex.Kind.MODULE)
                .contains("combatant.client.features.module.modules.visuals.Zoom"));
        assertTrue(CombatantIndex.classNames(CombatantIndex.Kind.SOUND)
                .contains("combatant.client.features.gui.hud.draggable.impl.HudNotifier$NotificationSound"));
        assertTrue(CombatantIndex.classNames(CombatantIndex.Kind.ASSET_HOOK)
                .contains("combatant.client.util.sound.SoundSystem"));
        assertTrue(CombatantIndex.classNames(CombatantIndex.Kind.CONFIG)
                .contains("combatant.client.features.relations.PlayerRelations"));
    }

    @Test
    void packageFilterDoesNotLeakOtherComponentPackages() {
        assertTrue(CombatantIndex.classNames(
                        CombatantIndex.Kind.COMMAND,
                        "combatant.client.features.command.impl"
                ).stream()
                .allMatch(name -> name.startsWith("combatant.client.features.command.impl.")));
    }
}
