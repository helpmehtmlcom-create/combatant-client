/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

import combatant.client.render.world.ClientLevelEnvironmentSeedView;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic Overworld weather field. Exact Minecraft rain/thunder/biome climate are inputs;
 * pressure/front/wind are explicitly model-owned continuous fields, never semantic guesses.
 */
public final class OverworldWeatherProvider implements WeatherProvider {
    private static final Identifier ID = DimensionRenderProfileRegistry.OVERWORLD_WEATHER;
    private static final int GRID = 9;
    private static final int SPACING = 32;
    private static final double PRESSURE_SCALE = 1.0 / 384.0;
    private static final double SECONDARY_SCALE = 1.0 / 1536.0;
    private static final double TICKS_PER_SECOND = 20.0;

    private int lastOriginX = Integer.MIN_VALUE;
    private int lastOriginZ = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private long lastWeatherBucket = Long.MIN_VALUE;
    private long revision;
    private WeatherFieldState cachedField = WeatherFieldState.EMPTY;

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public WeatherState capture(ClientLevel level,
                                Vec3 cameraPosition,
                                float partialTick,
                                BiomeClimateState climate,
                                long frameId) {
        if (level == null || cameraPosition == null) return WeatherState.NONE;
        long seed = level instanceof ClientLevelEnvironmentSeedView seeded
                ? seeded.combatant$environmentSeed() : mix64(level.dimension().identifier().hashCode());
        long clock = level.getOverworldClockTime();
        double modelTicks = clock + clamp01(partialTick);
        float rain = clamp01(level.getRainLevel(clamp01(partialTick)));
        float thunder = clamp01(level.getThunderLevel(clamp01(partialTick)));

        int centerX = floor(cameraPosition.x);
        int centerY = floor(cameraPosition.y);
        int centerZ = floor(cameraPosition.z);
        int originX = floorDiv(centerX, SPACING) * SPACING - (GRID / 2) * SPACING;
        int originZ = floorDiv(centerZ, SPACING) * SPACING - (GRID / 2) * SPACING;
        int sampleY = floorDiv(centerY, 16) * 16;
        long weatherBucket = (long) Math.floor(modelTicks / 20.0); // field evolves at 1 Hz; consumers interpolate spatially.

        if (originX != lastOriginX || originZ != lastOriginZ || sampleY != lastY || weatherBucket != lastWeatherBucket) {
            cachedField = rebuild(level, climate, seed, modelTicks, rain, thunder, originX, originZ, sampleY);
            lastOriginX = originX;
            lastOriginZ = originZ;
            lastY = sampleY;
            lastWeatherBucket = weatherBucket;
            revision++;
        }

        WeatherSample camera = evaluate(level, climate == null ? null : climate.camera(), seed, modelTicks,
                rain, thunder, centerX, centerY, centerZ);
        return new WeatherState(ID, camera, cachedField, rain, thunder, seed, (long) modelTicks, true);
    }

    @Override
    public void reset() {
        lastOriginX = Integer.MIN_VALUE;
        lastOriginZ = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;
        lastWeatherBucket = Long.MIN_VALUE;
        revision++;
        cachedField = WeatherFieldState.EMPTY;
    }

    private WeatherFieldState rebuild(ClientLevel level,
                                      BiomeClimateState climate,
                                      long seed,
                                      double timeTicks,
                                      float rain,
                                      float thunder,
                                      int originX,
                                      int originZ,
                                      int sampleY) {
        List<WeatherSample> samples = new ArrayList<>(GRID * GRID);
        for (int z = 0; z < GRID; z++) {
            for (int x = 0; x < GRID; x++) {
                int wx = originX + x * SPACING;
                int wz = originZ + z * SPACING;
                BiomeClimateSample biome = BiomeClimateSampler.sampleAt(level, wx, sampleY, wz);
                samples.add(evaluate(level, biome, seed, timeTicks, rain, thunder, wx, sampleY, wz));
            }
        }
        return new WeatherFieldState(samples, GRID, GRID, SPACING, originX, originZ, sampleY, revision + 1L, true);
    }

    private static WeatherSample evaluate(ClientLevel level,
                                          BiomeClimateSample biome,
                                          long seed,
                                          double timeTicks,
                                          float globalRain,
                                          float globalThunder,
                                          int x,
                                          int y,
                                          int z) {
        BiomeClimateSample b = biome != null && biome.valid() ? biome : BiomeClimateSample.UNKNOWN;
        double seconds = timeTicks / TICKS_PER_SECOND;

        // Two advected low-frequency scalar fields define synoptic pressure. Their gradients are
        // physical model data used to derive wind/fronts, not classifiers for world semantics.
        double driftX = seconds * 0.65;
        double driftZ = seconds * 0.22;
        double px = (x + driftX) * PRESSURE_SCALE;
        double pz = (z + driftZ) * PRESSURE_SCALE;
        double low = valueNoise(seed ^ 0x4F1BBCDCBFA54001L, px, pz);
        double broad = valueNoise(seed ^ 0x9E3779B97F4A7C15L,
                (x - seconds * 0.18) * SECONDARY_SCALE,
                (z + seconds * 0.31) * SECONDARY_SCALE);
        float pressure = clampSigned((float) (0.72 * low + 0.28 * broad));

        double e = 10.0 * PRESSURE_SCALE;
        double pL = pressureField(seed, px - e, pz, x, z, seconds);
        double pR = pressureField(seed, px + e, pz, x, z, seconds);
        double pD = pressureField(seed, px, pz - e, x, z, seconds);
        double pU = pressureField(seed, px, pz + e, x, z, seconds);
        float gradX = (float) ((pR - pL) / 20.0);
        float gradZ = (float) ((pU - pD) / 20.0);
        float gradient = (float) Math.sqrt(gradX * gradX + gradZ * gradZ);
        float frontStrength = smooth01(gradient * 190.0f);

        float biomeHumidity = b.valid() ? b.humidityBaseline() : 0.5f;
        float moistureField = clamp01(0.5f + 0.5f * (float) valueNoise(
                seed ^ 0xD1B54A32D192ED03L,
                (x + seconds * 0.42) / 640.0,
                (z - seconds * 0.16) / 640.0
        ));
        float humidity = clamp01(0.68f * biomeHumidity + 0.32f * moistureField
                + globalRain * 0.16f * (1.0f - biomeHumidity));
        float lowPressure = clamp01(0.5f - pressure * 0.5f);
        float stormPotential = clamp01(humidity * (0.45f + 0.55f * lowPressure)
                * (0.35f + 0.65f * frontStrength));
        stormPotential = clamp01(stormPotential + globalThunder * 0.35f);

        // Pressure-gradient-driven horizontal wind. Direction follows the model field and remains
        // continuous in world space/time; no biome category chooses a wind direction.
        float windScale = 2100.0f;
        float windX = -gradZ * windScale + (float) broad * 0.9f;
        float windZ = gradX * windScale + (float) low * 0.9f;
        float frontSpeed = 0.55f + 2.1f * frontStrength;
        float gradLen = Math.max(1.0e-5f, gradient);
        float frontVX = -gradX / gradLen * frontSpeed;
        float frontVZ = -gradZ / gradLen * frontSpeed;

        PrecipitationKind precipitation = b.valid() ? b.precipitation() : PrecipitationKind.NONE;
        float precipitationIntensity = precipitation == PrecipitationKind.NONE
                ? 0.0f
                : clamp01(globalRain * (0.35f + 0.65f * stormPotential));

        return new WeatherSample(
                x, y, z,
                b.valid() ? b.effectiveTemperature() : 0.0f,
                humidity,
                pressure,
                stormPotential,
                precipitationIntensity,
                precipitation,
                windX, 0.0f, windZ,
                frontStrength,
                frontVX, frontVZ,
                SurfaceDepositionKind.NONE, 0.0f,
                true
        );
    }

    private static double pressureField(long seed, double scaledX, double scaledZ,
                                        int worldX, int worldZ, double seconds) {
        double broad = valueNoise(seed ^ 0x9E3779B97F4A7C15L,
                (worldX - seconds * 0.18) * SECONDARY_SCALE,
                (worldZ + seconds * 0.31) * SECONDARY_SCALE);
        return 0.72 * valueNoise(seed ^ 0x4F1BBCDCBFA54001L, scaledX, scaledZ) + 0.28 * broad;
    }

    private static double valueNoise(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = hashUnit(seed, x0, z0);
        double b = hashUnit(seed, x0 + 1, z0);
        double c = hashUnit(seed, x0, z0 + 1);
        double d = hashUnit(seed, x0 + 1, z0 + 1);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }

    private static double hashUnit(long seed, int x, int z) {
        long h = seed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x9E3779B97F4A7C15L);
        h = mix64(h);
        return ((h >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double smooth(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static float smooth01(float value) {
        float t = clamp01(value);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float clampSigned(float value) {
        if (!Float.isFinite(value)) return 0.0f;
        return Math.max(-1.0f, Math.min(1.0f, value));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int floorDiv(int value, int divisor) {
        return Math.floorDiv(value, divisor);
    }
}
