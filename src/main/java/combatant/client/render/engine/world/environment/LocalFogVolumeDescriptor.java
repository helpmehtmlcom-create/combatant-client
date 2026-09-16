/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.world.environment;

/**
 * Exact world-space participating-medium volume supplied by scene/environment producers.
 * Optical coefficients and emission are per block at density 1.0.
 */
public record LocalFogVolumeDescriptor(
        long stableId,
        LocalFogVolumeShape shape,
        double centerX,
        double centerY,
        double centerZ,
        float extentX,
        float extentY,
        float extentZ,
        float edgeFadeBlocks,
        float density,
        float scatteringRed,
        float scatteringGreen,
        float scatteringBlue,
        float absorptionRed,
        float absorptionGreen,
        float absorptionBlue,
        float anisotropy,
        float emissionRed,
        float emissionGreen,
        float emissionBlue,
        float priority,
        boolean valid
) {
    public LocalFogVolumeDescriptor {
        if (shape == null) shape = LocalFogVolumeShape.SPHERE;
        boolean centerValid = Double.isFinite(centerX) && Double.isFinite(centerY) && Double.isFinite(centerZ);
        centerX = finite(centerX);
        centerY = finite(centerY);
        centerZ = finite(centerZ);
        extentX = nonNegative(extentX);
        extentY = nonNegative(extentY);
        extentZ = nonNegative(extentZ);
        if (shape == LocalFogVolumeShape.SPHERE) {
            extentY = extentX;
            extentZ = extentX;
        }
        edgeFadeBlocks = clamp(nonNegative(edgeFadeBlocks), 0.0f,
                Math.max(0.0f, Math.min(extentX, Math.min(extentY, extentZ))));
        density = nonNegative(density);
        scatteringRed = nonNegative(scatteringRed);
        scatteringGreen = nonNegative(scatteringGreen);
        scatteringBlue = nonNegative(scatteringBlue);
        absorptionRed = nonNegative(absorptionRed);
        absorptionGreen = nonNegative(absorptionGreen);
        absorptionBlue = nonNegative(absorptionBlue);
        anisotropy = clamp(anisotropy, -0.95f, 0.95f);
        emissionRed = nonNegative(emissionRed);
        emissionGreen = nonNegative(emissionGreen);
        emissionBlue = nonNegative(emissionBlue);
        priority = Float.isFinite(priority) ? priority : 0.0f;
        if (!centerValid || extentX <= 0.0f || extentY <= 0.0f || extentZ <= 0.0f
                || density <= 0.0f || !hasOpticalContribution(
                scatteringRed, scatteringGreen, scatteringBlue,
                absorptionRed, absorptionGreen, absorptionBlue,
                emissionRed, emissionGreen, emissionBlue)) {
            valid = false;
        }
    }

    public static LocalFogVolumeDescriptor sphere(long stableId,
                                                  double centerX,
                                                  double centerY,
                                                  double centerZ,
                                                  float radius,
                                                  float edgeFadeBlocks,
                                                  float density,
                                                  float scatteringRed,
                                                  float scatteringGreen,
                                                  float scatteringBlue,
                                                  float absorptionRed,
                                                  float absorptionGreen,
                                                  float absorptionBlue,
                                                  float anisotropy,
                                                  float emissionRed,
                                                  float emissionGreen,
                                                  float emissionBlue,
                                                  float priority) {
        return new LocalFogVolumeDescriptor(
                stableId, LocalFogVolumeShape.SPHERE,
                centerX, centerY, centerZ,
                radius, radius, radius,
                edgeFadeBlocks, density,
                scatteringRed, scatteringGreen, scatteringBlue,
                absorptionRed, absorptionGreen, absorptionBlue,
                anisotropy,
                emissionRed, emissionGreen, emissionBlue,
                priority, true
        );
    }

    public static LocalFogVolumeDescriptor box(long stableId,
                                               double centerX,
                                               double centerY,
                                               double centerZ,
                                               float halfExtentX,
                                               float halfExtentY,
                                               float halfExtentZ,
                                               float edgeFadeBlocks,
                                               float density,
                                               float scatteringRed,
                                               float scatteringGreen,
                                               float scatteringBlue,
                                               float absorptionRed,
                                               float absorptionGreen,
                                               float absorptionBlue,
                                               float anisotropy,
                                               float emissionRed,
                                               float emissionGreen,
                                               float emissionBlue,
                                               float priority) {
        return new LocalFogVolumeDescriptor(
                stableId, LocalFogVolumeShape.BOX,
                centerX, centerY, centerZ,
                halfExtentX, halfExtentY, halfExtentZ,
                edgeFadeBlocks, density,
                scatteringRed, scatteringGreen, scatteringBlue,
                absorptionRed, absorptionGreen, absorptionBlue,
                anisotropy,
                emissionRed, emissionGreen, emissionBlue,
                priority, true
        );
    }

    public double distanceToBoundsSquared(double x, double y, double z) {
        double dx = Math.abs(x - centerX) - extentX;
        double dy = Math.abs(y - centerY) - extentY;
        double dz = Math.abs(z - centerZ) - extentZ;
        if (shape == LocalFogVolumeShape.SPHERE) {
            double cx = x - centerX;
            double cy = y - centerY;
            double cz = z - centerZ;
            double distance = Math.max(0.0, Math.sqrt(cx * cx + cy * cy + cz * cz) - extentX);
            return distance * distance;
        }
        dx = Math.max(0.0, dx);
        dy = Math.max(0.0, dy);
        dz = Math.max(0.0, dz);
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean hasOpticalContribution(float... values) {
        for (float value : values) if (value > 0.0f) return true;
        return false;
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static float nonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        if (!Float.isFinite(value)) return minimum;
        return Math.max(minimum, Math.min(maximum, value));
    }
}
