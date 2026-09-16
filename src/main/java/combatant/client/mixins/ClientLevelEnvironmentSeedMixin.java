/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.mixins;

import combatant.client.render.world.ClientLevelEnvironmentSeedView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the exact seed already supplied to ClientLevel for deterministic renderer weather fields. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelEnvironmentSeedMixin implements ClientLevelEnvironmentSeedView {
    @Unique
    private long combatant$environmentSeed;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void combatant$captureEnvironmentSeed(ClientPacketListener connection,
                                                   ClientLevel.ClientLevelData levelData,
                                                   ResourceKey<Level> dimension,
                                                   Holder<DimensionType> dimensionType,
                                                   int viewDistance,
                                                   int simulationDistance,
                                                   LevelExtractor extractor,
                                                   boolean debug,
                                                   long seed,
                                                   int seaLevel,
                                                   CallbackInfo ci) {
        combatant$environmentSeed = seed;
    }

    @Override
    public long combatant$environmentSeed() {
        return combatant$environmentSeed;
    }
}
