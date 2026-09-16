/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.accessors;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accessor for Biome.ClimateSettings.downfall, which has no public 26.2 getter. */
@Mixin(targets = "net.minecraft.world.level.biome.Biome$ClimateSettings")
public interface BiomeClimateSettingsAccessor {
    @Accessor("downfall")
    float combatant$getDownfall();
}
