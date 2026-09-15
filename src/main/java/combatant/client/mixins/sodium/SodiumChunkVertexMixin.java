/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import combatant.client.render.sodium.terrain.CombatantChunkVertexExtension;

@Mixin(ChunkVertexEncoder.Vertex.class)
public abstract class SodiumChunkVertexMixin implements CombatantChunkVertexExtension {
    @Unique
    private int combatant$surfaceFlags;
    @Unique
    private int combatant$materialId;
    @Unique
    private int combatant$materialMapPresenceMask;
    @Unique
    private int combatant$materialFeatureMask;
    @Unique
    private int combatant$packedScalarSurface;

    @Inject(method = "copyVertexTo", at = @At("HEAD"))
    private static void combatant$copyVertexData(ChunkVertexEncoder.Vertex from, ChunkVertexEncoder.Vertex to, CallbackInfo ci) {
        ((CombatantChunkVertexExtension) from).combatant$copyData((CombatantChunkVertexExtension) to);
    }

    @Override
    public void combatant$setSurfaceFlags(int surfaceFlags) {
        this.combatant$surfaceFlags = surfaceFlags;
    }

    @Override
    public int combatant$getSurfaceFlags() {
        return this.combatant$surfaceFlags;
    }

    @Override
    public void combatant$setMaterialData(int materialId, int mapPresenceMask, int featureMask, int packedScalarSurface) {
        this.combatant$materialId = materialId;
        this.combatant$materialMapPresenceMask = mapPresenceMask & 0xFF;
        this.combatant$materialFeatureMask = featureMask & 0xFFFF;
        this.combatant$packedScalarSurface = packedScalarSurface;
    }

    @Override
    public int combatant$getMaterialId() {
        return this.combatant$materialId;
    }

    @Override
    public int combatant$getMaterialMapPresenceMask() {
        return this.combatant$materialMapPresenceMask;
    }

    @Override
    public int combatant$getMaterialFeatureMask() {
        return this.combatant$materialFeatureMask;
    }

    @Override
    public int combatant$getPackedScalarSurface() {
        return this.combatant$packedScalarSurface;
    }

    @Override
    public void combatant$copyData(CombatantChunkVertexExtension dest) {
        dest.combatant$setSurfaceFlags(this.combatant$surfaceFlags);
        dest.combatant$setMaterialData(
                this.combatant$materialId,
                this.combatant$materialMapPresenceMask,
                this.combatant$materialFeatureMask,
                this.combatant$packedScalarSurface
        );
    }
}
