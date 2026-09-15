/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Frame-local directional-light cascade source.
 *
 * <p>This is geometry preparation, not a shadow renderer: it turns the authoritative primary
 * camera and Minecraft sun state into stable secondary view/projection contracts. A later
 * SHADOW_MAP producer can render those views into an atlas/array without reconstructing camera
 * state or owning cascade split policy itself.</p>
 */
final class DeferredShadowCascadeSource {
    static final int MAX_CASCADE_COUNT = 8;
    private static final float DEFAULT_NEAR_DISTANCE = 0.1f;
    private static final float BOUNDS_PADDING = 2.0f;

    void prepare(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        if (primary == null || !primary.hasSunAngle()) return;

        int cascadeCount = context.settings().shadowCascadeCount();
        float farDistance = primary.farPlane();
        if (!(farDistance > DEFAULT_NEAR_DISTANCE)) {
            Minecraft minecraft = Minecraft.getInstance();
            farDistance = minecraft != null && minecraft.options != null
                    ? Math.max(16.0f, minecraft.options.getEffectiveRenderDistance() * 16.0f)
                    : 128.0f;
        }
        float nearDistance = Math.min(DEFAULT_NEAR_DISTANCE, farDistance * 0.25f);
        float lambda = context.settings().shadowSplitLambda();

        float[] splits = new float[cascadeCount + 1];
        splits[0] = nearDistance;
        for (int i = 1; i <= cascadeCount; i++) {
            float p = (float) i / (float) cascadeCount;
            float logarithmic = (float) (nearDistance * Math.pow(farDistance / nearDistance, p));
            float uniform = nearDistance + (farDistance - nearDistance) * p;
            splits[i] = lerp(uniform, logarithmic, lambda);
        }
        splits[cascadeCount] = farDistance;

        Matrix4f inverseView = primary.inverseView();
        Matrix4f projection = primary.projection();
        Vector3f lightDirection = new Vector3f(
                (float) -Math.sin(primary.sunAngle()),
                (float) Math.cos(primary.sunAngle()),
                0.0f
        ).normalize();

        int resolution = context.settings().shadowResolution();
        float blendFraction = context.settings().shadowCascadeBlendFraction();
        int columns = (int) Math.ceil(Math.sqrt(cascadeCount));
        for (int cascade = 0; cascade < cascadeCount; cascade++) {
            float cascadeNear = splits[cascade];
            float cascadeFar = splits[cascade + 1];
            float coverageNear = cascadeNear;
            if (cascade > 0 && blendFraction > 0.0f) {
                float previousSpan = splits[cascade] - splits[cascade - 1];
                coverageNear = Math.max(nearDistance, cascadeNear - previousSpan * blendFraction);
            }
            DeferredSecondaryView view = buildCascade(
                    cascade,
                    cascadeNear,
                    cascadeFar,
                    coverageNear,
                    cascadeFar,
                    primary.cameraPosition(),
                    projection,
                    inverseView,
                    lightDirection,
                    context.settings().shadowCasterDistance(),
                    resolution,
                    (cascade % columns) * resolution,
                    (cascade / columns) * resolution
            );
            context.secondaryViews().register(view);
        }
    }

    private static DeferredSecondaryView buildCascade(int index,
                                                       float nearDistance,
                                                       float farDistance,
                                                       float coverageNearDistance,
                                                       float coverageFarDistance,
                                                       Vec3 cameraOrigin,
                                                       Matrix4fc cameraProjection,
                                                       Matrix4fc inverseCameraView,
                                                       Vector3f lightDirection,
                                                       float casterDistance,
                                                       int resolution,
                                                       int viewportX,
                                                       int viewportY) {
        Vector3f[] corners = frustumCorners(coverageNearDistance, coverageFarDistance, cameraProjection, inverseCameraView);
        Vector3f center = new Vector3f();
        for (Vector3f corner : corners) center.add(corner);
        center.div((float) corners.length);

        float radius = 0.0f;
        for (Vector3f corner : corners) {
            radius = Math.max(radius, corner.distance(center));
        }
        radius = Math.max(1.0f, radius);

        Vector3f up = Math.abs(lightDirection.y) > 0.95f
                ? new Vector3f(0.0f, 0.0f, 1.0f)
                : new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f eye = new Vector3f(center).fma(radius + casterDistance + BOUNDS_PADDING, lightDirection);
        Matrix4f lightView = new Matrix4f().lookAt(eye, center, up);

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        Vector3f transformed = new Vector3f();
        for (Vector3f corner : corners) {
            transformPosition(lightView, corner, transformed);
            minX = Math.min(minX, transformed.x);
            minY = Math.min(minY, transformed.y);
            minZ = Math.min(minZ, transformed.z);
            maxX = Math.max(maxX, transformed.x);
            maxY = Math.max(maxY, transformed.y);
            maxZ = Math.max(maxZ, transformed.z);
        }

        minX -= BOUNDS_PADDING;
        minY -= BOUNDS_PADDING;
        maxX += BOUNDS_PADDING;
        maxY += BOUNDS_PADDING;

        // Snap the orthographic window to whole shadow texels. This is source stability, not
        // artistic filtering: camera sub-texel motion must not continuously move the projection.
        float extentX = Math.max(0.001f, maxX - minX);
        float extentY = Math.max(0.001f, maxY - minY);
        float texelX = extentX / (float) resolution;
        float texelY = extentY / (float) resolution;
        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        centerX = Math.round(centerX / texelX) * texelX;
        centerY = Math.round(centerY / texelY) * texelY;
        minX = centerX - extentX * 0.5f;
        maxX = centerX + extentX * 0.5f;
        minY = centerY - extentY * 0.5f;
        maxY = centerY + extentY * 0.5f;

        // Keep an explicit caster band on the light-facing side of the receiver frustum.
        // Extending only the far plane would include geometry behind the receivers while clipping
        // exactly the off-screen/above-camera casters this secondary visibility path exists for.
        float lightNear = Math.max(0.01f, -maxZ - casterDistance - BOUNDS_PADDING);
        float lightFar = Math.max(lightNear + 0.01f, -minZ + BOUNDS_PADDING);
        // Combatant/Minecraft depth is reversed-Z (GREATER/GEQUAL with clear depth 0). JOML's
        // ordinary ortho maps the geometric near plane to the low depth end, so swap the z planes
        // to keep shadow depth in the same convention as MAIN_DEPTH/Hi-Z on both backends.
        Matrix4f lightProjection = new Matrix4f().setOrtho(
                minX, maxX, minY, maxY, lightFar, lightNear
        );

        return new DeferredSecondaryView(
                DeferredViewFamily.SHADOW_CASCADE,
                index,
                "shadow.cascade." + index,
                lightView,
                lightProjection,
                cameraOrigin,
                0,
                viewportX,
                viewportY,
                resolution,
                resolution,
                nearDistance,
                farDistance
        );
    }

    private static Vector3f[] frustumCorners(float nearDistance,
                                              float farDistance,
                                              Matrix4fc projection,
                                              Matrix4fc inverseView) {
        Vector3f[] corners = new Vector3f[8];
        int cursor = 0;
        for (float distance : new float[]{nearDistance, farDistance}) {
            for (int y = 0; y < 2; y++) {
                float ndcY = y == 0 ? -1.0f : 1.0f;
                for (int x = 0; x < 2; x++) {
                    float ndcX = x == 0 ? -1.0f : 1.0f;
                    float viewX = distance * (ndcX + projection.m20()) / projection.m00();
                    float viewY = distance * (ndcY + projection.m21()) / projection.m11();
                    float viewZ = -distance;
                    corners[cursor++] = transformDirection(
                            inverseView, new Vector3f(viewX, viewY, viewZ), new Vector3f()
                    );
                }
            }
        }
        return corners;
    }

    private static Vector3f transformDirection(Matrix4fc matrix, Vector3f source, Vector3f dest) {
        float x = source.x;
        float y = source.y;
        float z = source.z;
        return dest.set(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z,
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z,
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z
        );
    }

    private static Vector3f transformPosition(Matrix4fc matrix, Vector3f source, Vector3f dest) {
        float x = source.x;
        float y = source.y;
        float z = source.z;
        return dest.set(
                matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30(),
                matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31(),
                matrix.m02() * x + matrix.m12() * y + matrix.m22() * z + matrix.m32()
        );
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
