/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.visibility;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * Backend-neutral stable frustum operating on world-space AABBs.
 *
 * <p>The view matrix is expected to transform camera-relative world positions. This matches the
 * Minecraft/Combatant world matrix contract: translation is supplied separately through the camera
 * origin, while the matrix contains the view rotation. Plane extraction explicitly handles both
 * OpenGL [-1, 1] and Vulkan/zero-to-one clip-depth conventions.</p>
 */
public final class SceneFrustum {
    private static final int PLANE_COUNT = 6;
    private final float[] planes = new float[PLANE_COUNT * 4];
    private final Vec3 cameraPosition;

    public SceneFrustum(Matrix4fc view, Matrix4fc projection, Vec3 cameraPosition, boolean zeroToOneDepth) {
        if (view == null) throw new IllegalArgumentException("view matrix is required");
        if (projection == null) throw new IllegalArgumentException("projection matrix is required");
        this.cameraPosition = cameraPosition == null ? Vec3.ZERO : cameraPosition;

        Matrix4f clip = new Matrix4f(projection).mul(view);

        // JOML transforms column vectors. Matrix rows are therefore:
        // row0=(m00,m10,m20,m30), row1=(m01,m11,m21,m31), ...
        setPlane(0,
                clip.m03() + clip.m00(), clip.m13() + clip.m10(),
                clip.m23() + clip.m20(), clip.m33() + clip.m30()); // left
        setPlane(1,
                clip.m03() - clip.m00(), clip.m13() - clip.m10(),
                clip.m23() - clip.m20(), clip.m33() - clip.m30()); // right
        setPlane(2,
                clip.m03() + clip.m01(), clip.m13() + clip.m11(),
                clip.m23() + clip.m21(), clip.m33() + clip.m31()); // bottom
        setPlane(3,
                clip.m03() - clip.m01(), clip.m13() - clip.m11(),
                clip.m23() - clip.m21(), clip.m33() - clip.m31()); // top

        if (zeroToOneDepth) {
            // 0 <= clip.z <= clip.w. Reverse-Z changes which geometric plane is near/far, not
            // the valid clip interval, so the same inequalities remain correct.
            setPlane(4, clip.m02(), clip.m12(), clip.m22(), clip.m32());
        } else {
            // -clip.w <= clip.z <= clip.w.
            setPlane(4,
                    clip.m03() + clip.m02(), clip.m13() + clip.m12(),
                    clip.m23() + clip.m22(), clip.m33() + clip.m32());
        }
        setPlane(5,
                clip.m03() - clip.m02(), clip.m13() - clip.m12(),
                clip.m23() - clip.m22(), clip.m33() - clip.m32());
    }

    public boolean isVisible(AABB box) {
        if (box == null) return true;
        double cameraX = cameraPosition.x;
        double cameraY = cameraPosition.y;
        double cameraZ = cameraPosition.z;

        for (int plane = 0; plane < PLANE_COUNT; plane++) {
            int base = plane * 4;
            float nx = planes[base];
            float ny = planes[base + 1];
            float nz = planes[base + 2];
            float d = planes[base + 3];

            // Positive support vertex. If even the furthest point in the plane-normal direction
            // lies outside, the complete AABB is outside this plane.
            double x = (nx >= 0.0f ? box.maxX : box.minX) - cameraX;
            double y = (ny >= 0.0f ? box.maxY : box.minY) - cameraY;
            double z = (nz >= 0.0f ? box.maxZ : box.minZ) - cameraZ;
            if (nx * x + ny * y + nz * z + d < 0.0) return false;
        }
        return true;
    }

    public boolean isSphereVisible(double worldX, double worldY, double worldZ, double radius) {
        double x = worldX - cameraPosition.x;
        double y = worldY - cameraPosition.y;
        double z = worldZ - cameraPosition.z;
        double r = Math.max(0.0, radius);
        for (int plane = 0; plane < PLANE_COUNT; plane++) {
            int base = plane * 4;
            double distance = planes[base] * x + planes[base + 1] * y + planes[base + 2] * z + planes[base + 3];
            if (distance < -r) return false;
        }
        return true;
    }

    private void setPlane(int index, float a, float b, float c, float d) {
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        int base = index * 4;
        if (!(length > 1.0e-8f) || !Float.isFinite(length)) {
            // Degenerate plane is fail-open. This can happen only for a malformed/custom projection.
            planes[base] = 0.0f;
            planes[base + 1] = 0.0f;
            planes[base + 2] = 0.0f;
            planes[base + 3] = Float.POSITIVE_INFINITY;
            return;
        }
        float inverse = 1.0f / length;
        planes[base] = a * inverse;
        planes[base + 1] = b * inverse;
        planes[base + 2] = c * inverse;
        planes[base + 3] = d * inverse;
    }
}
