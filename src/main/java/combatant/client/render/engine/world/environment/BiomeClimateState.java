/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import java.util.List;

/** Camera-centered spatial biome contract shared by every dimension. */
public record BiomeClimateState(
        BiomeClimateSample camera,
        List<BiomeClimateSample> samples,
        int gridWidth,
        int gridDepth,
        int spacingBlocks,
        int originBlockX,
        int originBlockZ,
        int sampleBlockY,
        long revision,
        boolean valid
) {
    public static final BiomeClimateState EMPTY = new BiomeClimateState(
            BiomeClimateSample.UNKNOWN, List.of(), 0, 0, 0, 0, 0, 0, 0L, false
    );

    public BiomeClimateState {
        if (camera == null) camera = BiomeClimateSample.UNKNOWN;
        samples = samples == null ? List.of() : List.copyOf(samples);
        gridWidth = Math.max(0, gridWidth);
        gridDepth = Math.max(0, gridDepth);
        spacingBlocks = Math.max(0, spacingBlocks);
    }

    public BiomeClimateSample sample(int gridX, int gridZ) {
        if (gridX < 0 || gridZ < 0 || gridX >= gridWidth || gridZ >= gridDepth) {
            return BiomeClimateSample.UNKNOWN;
        }
        int index = gridZ * gridWidth + gridX;
        return index < samples.size() ? samples.get(index) : BiomeClimateSample.UNKNOWN;
    }
}
