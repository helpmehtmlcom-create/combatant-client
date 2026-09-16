/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.mixininterface.IBiome;
import combatant.client.mixins.accessors.BiomeAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Incremental camera-centered biome field. Sampling is discrete; interpolation belongs to consumers. */
public final class BiomeClimateSampler {
    private static final int GRID = 9;
    private static final int SPACING = 8;
    private static final Identifier UNKNOWN = Identifier.fromNamespaceAndPath("combatant", "unknown_biome");

    private Object worldOwner;
    private int lastOriginX = Integer.MIN_VALUE;
    private int lastOriginZ = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private long revision;
    private BiomeClimateState cached = BiomeClimateState.EMPTY;

    public BiomeClimateState capture(ClientLevel level, Vec3 cameraPosition) {
        if (level == null || cameraPosition == null) return BiomeClimateState.EMPTY;
        if (worldOwner != level) {
            reset();
            worldOwner = level;
        }

        int cx = (int) Math.floor(cameraPosition.x);
        int cy = (int) Math.floor(cameraPosition.y);
        int cz = (int) Math.floor(cameraPosition.z);
        int sampleY = Math.floorDiv(cy, SPACING) * SPACING;
        int originX = Math.floorDiv(cx, SPACING) * SPACING - (GRID / 2) * SPACING;
        int originZ = Math.floorDiv(cz, SPACING) * SPACING - (GRID / 2) * SPACING;

        if (originX != lastOriginX || originZ != lastOriginZ || sampleY != lastY) {
            List<BiomeClimateSample> samples = new ArrayList<>(GRID * GRID);
            for (int z = 0; z < GRID; z++) {
                for (int x = 0; x < GRID; x++) {
                    samples.add(sampleAt(level, originX + x * SPACING, sampleY, originZ + z * SPACING));
                }
            }
            revision++;
            cached = new BiomeClimateState(
                    sampleAt(level, cx, cy, cz), samples, GRID, GRID, SPACING,
                    originX, originZ, sampleY, revision, true
            );
            lastOriginX = originX;
            lastOriginZ = originZ;
            lastY = sampleY;
        } else {
            // Camera sample is exact even while the surrounding grid remains reusable.
            cached = new BiomeClimateState(
                    sampleAt(level, cx, cy, cz), cached.samples(), cached.gridWidth(), cached.gridDepth(),
                    cached.spacingBlocks(), cached.originBlockX(), cached.originBlockZ(), cached.sampleBlockY(),
                    cached.revision(), cached.valid()
            );
        }
        return cached;
    }

    public void reset() {
        worldOwner = null;
        lastOriginX = Integer.MIN_VALUE;
        lastOriginZ = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;
        revision++;
        cached = BiomeClimateState.EMPTY;
    }

    public static BiomeClimateSample sampleAt(ClientLevel level, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        Holder<Biome> holder = level.getBiome(pos);
        if (holder == null || holder.value() == null) return BiomeClimateSample.UNKNOWN;
        Biome biome = holder.value();
        Identifier key = holder.unwrapKey().map(resourceKey -> resourceKey.identifier()).orElse(UNKNOWN);
        float localTemperature = biome.getBaseTemperature();
        boolean localTemperatureValid = false;
        try {
            localTemperature = ((BiomeAccessor) (Object) biome).combatant$getLocalTemperature(pos, level.getSeaLevel());
            localTemperatureValid = true;
        } catch (Throwable ignored) {
            // Keep baseTemperature as an explicit fallback; validity tells consumers it is not altitude-adjusted.
        }
        float downfall = 0.0f;
        boolean downfallValid = false;
        downfall = ((IBiome) (Object) biome).combatant$getDownfall();
        downfallValid = true;
        Biome.Precipitation precipitation = level.getPrecipitationAt(pos);
        return new BiomeClimateSample(
                key, x, y, z,
                biome.getBaseTemperature(), localTemperature, localTemperatureValid,
                downfall, downfallValid, biome.hasPrecipitation(),
                PrecipitationKind.fromMinecraft(precipitation),
                biome.getWaterColor(), biome.getGrassColor(x, z), biome.getFoliageColor(), biome.getDryFoliageColor(),
                true
        );
    }
}
