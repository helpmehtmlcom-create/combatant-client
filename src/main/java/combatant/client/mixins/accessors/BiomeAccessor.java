/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins.accessors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exact access to the biome climate record; used only to expose data Minecraft already owns. */
@Mixin(Biome.class)
public interface BiomeAccessor {
    @Accessor("climateSettings")
    Object combatant$getClimateSettings();

    @Invoker("getTemperature")
    float combatant$getLocalTemperature(BlockPos pos, int seaLevel);
}
