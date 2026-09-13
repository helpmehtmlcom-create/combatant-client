/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.sound;

import combatant.client.util.sound.SoundAsset;
import combatant.client.util.sound.SoundCatalog;
import combatant.client.util.sound.SoundKey;
import combatant.client.util.sound.SoundOptions;

/** Distinct lifecycle feedback for ErrorHandler quarantine/recovery transactions. */
@SoundCatalog(namespace = "combatant", root = "sounds", idPrefix = "module_lifecycle")
public enum ModuleLifecycleSound implements SoundKey {
    @SoundAsset(value = "disable/heavydisable.wav", id = "hard_disable")
    HARD_DISABLE,
    @SoundAsset(value = "enable/heavyenable.wav", id = "recovery_enable")
    RECOVERY_ENABLE;

    public void feedback() {
        play(SoundOptions.gain(0.9));
    }
}
