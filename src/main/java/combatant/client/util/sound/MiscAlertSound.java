/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

@SoundCatalog(namespace = "combatant", root = "sounds/misc", idPrefix = "alerts")
public enum MiscAlertSound implements SoundKey {
    @SoundAsset(value = "warning.wav", id = "warning")
    WARNING
}
