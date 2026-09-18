/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import combatant.client.render.engine.world.DirectionalLightDescriptor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Frame-local directional-light cascade source.
 *
 * <p>This is geometry preparation, not a shadow renderer: it turns the authoritative primary
 * camera and explicit directional-light state into stable secondary view/projection contracts. A later
 * SHADOW_MAP producer can render those views into an atlas/array without reconstructing camera
 * state or owning cascade split policy itself.</p>
 */
final class DeferredShadowCascadeSource {
    static final int MAX_CASCADE_COUNT = 8;
    private static final float DEFAULT_NEAR_DISTANCE = 0.1f;
    private static final float BOUNDS_PADDING = 2.0f;
    private static final float RADIUS_QUANTIZATION = 16.0f;
    private static final float BASIS_EPSILON_SQUARED = 1.0e-6f;

    /**
     * Parallel-transported light up vector. A hard Y/Z helper-axis threshold makes the whole CSM
     * basis jump when the celestial direction crosses that threshold; carrying the previous up
     * vector through the new light plane keeps slow celestial motion continuous instead.
     */
    private final Vector3f previousLightDirection = new Vector3f();
    private final Vector3f previousLightUp = new Vector3f();
    private boolean lightBasisValid;

    void prepare(DeferredPassContext context) {
        DeferredPrimaryViewSource.FrameView primary = context.primaryView().current();
        DirectionalLightDescriptor directional = context.worldState().directionalLight();
        if (primary == null || !directional.shadowValid()) return;

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
        // CSM geometry must be invariant under TAA sample jitter. The primary scene may render
        // with the jittered projection, but cascade splits/frustum corners are a stable world-space
        // contract and therefore derive from the authoritative unjittered camera projection.
        Matrix4f projection = primary.unjitteredProjection();
        Vector3f lightDirection = directional.direction(new Vector3f());
        Vector3f lightUp = stableLightUp(lightDirection);

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
                    lightUp,
                    context.settings().shadowCasterDistance(),
                    resolution,
                    (cascade % columns) * resolution,
                    (cascade / columns) * resolution,
                    context.rhi().capabilities().zeroToOneDepth()
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
                                                       Vector3f lightUp,
                                                       float casterDistance,
                                                       int resolution,
                                                       int viewportX,
                                                       int viewportY,
                                                       boolean zeroToOneDepth) {
        Vector3f[] corners = frustumCorners(coverageNearDistance, coverageFarDistance, cameraProjection, inverseCameraView);
        Vector3f center = new Vector3f();
        for (Vector3f corner : corners) center.add(corner);
        center.div((float) corners.length);

        float radius = 0.0f;
        for (Vector3f corner : corners) {
            radius = Math.max(radius, corner.distance(center));
        }
        // Stable CSM uses a rotation-invariant square receiver extent. A tight light-space AABB
        // changes width/height while the primary camera rotates, which changes world-units per
        // shadow texel every frame even when the light and split distances are fixed.
        radius = Math.max(1.0f, radius + BOUNDS_PADDING);
        radius = (float) Math.ceil(radius * RADIUS_QUANTIZATION) / RADIUS_QUANTIZATION;

        Vector3f eye = new Vector3f(center).fma(radius + casterDistance + BOUNDS_PADDING, lightDirection);
        Matrix4f lightView = new Matrix4f().lookAt(eye, center, lightUp);

        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        Vector3f transformed = new Vector3f();
        for (Vector3f corner : corners) {
            transformPosition(lightView, corner, transformed);
            minZ = Math.min(minZ, transformed.z);
            maxZ = Math.max(maxZ, transformed.z);
        }

        // The renderer is camera-relative, so snapping a camera-relative center is a no-op under
        // camera translation. Anchor the square projection to the ABSOLUTE world-space light grid,
        // then express the small snapped offset back in this camera-relative light view.
        float extent = Math.max(0.001f, radius * 2.0f);
        float worldTexel = extent / (float) Math.max(1, resolution);
        Vector3f absoluteCenter = new Vector3f(
                (float) (cameraOrigin.x + center.x),
                (float) (cameraOrigin.y + center.y),
                (float) (cameraOrigin.z + center.z)
        );
        Vector3f absoluteCenterLight = transformDirection(lightView, absoluteCenter, new Vector3f());
        float snappedWorldX = Math.round(absoluteCenterLight.x / worldTexel) * worldTexel;
        float snappedWorldY = Math.round(absoluteCenterLight.y / worldTexel) * worldTexel;
        float deltaX = absoluteCenterLight.x - snappedWorldX;
        float deltaY = absoluteCenterLight.y - snappedWorldY;
        float minX = -radius - deltaX;
        float maxX = radius - deltaX;
        float minY = -radius - deltaY;
        float maxY = radius - deltaY;

        // Keep an explicit caster band on the light-facing side of the receiver frustum.
        // Extending only the far plane would include geometry behind the receivers while clipping
        // exactly the off-screen/above-camera casters this secondary visibility path exists for.
        float lightNear = Math.max(0.01f, -maxZ - casterDistance - BOUNDS_PADDING);
        float lightFar = Math.max(lightNear + 0.01f, -minZ + BOUNDS_PADDING);
        // Combatant/Minecraft depth is reversed-Z (GREATER/GEQUAL with clear depth 0). JOML's
        // ordinary ortho maps the geometric near plane to the low depth end, so swap the z planes
        // to keep shadow depth in the same convention as MAIN_DEPTH/Hi-Z on both backends.
        Matrix4f lightProjection = new Matrix4f().setOrtho(
                minX, maxX, minY, maxY, lightFar, lightNear, zeroToOneDepth
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

    private Vector3f stableLightUp(Vector3f lightDirection) {
        Vector3f direction = new Vector3f(lightDirection).normalize();
        Vector3f up = new Vector3f();

        if (lightBasisValid && previousLightDirection.dot(direction) > 0.5f) {
            // Parallel transport the previous up vector into the plane perpendicular to the new
            // light direction. This keeps orientation continuous for normal celestial movement.
            up.set(previousLightUp).fma(-previousLightUp.dot(direction), direction);
            if (up.lengthSquared() > BASIS_EPSILON_SQUARED) {
                up.normalize();
                if (up.dot(previousLightUp) < 0.0f) up.negate();
            } else {
                up.zero();
            }
        }

        if (up.lengthSquared() <= BASIS_EPSILON_SQUARED) {
            // Initial/fallback basis: pick the world cardinal axis least parallel to the light,
            // then project it onto the light plane. No near-parallel Y/Z threshold is involved.
            float ax = Math.abs(direction.x);
            float ay = Math.abs(direction.y);
            float az = Math.abs(direction.z);
            Vector3f helper = ax <= ay && ax <= az
                    ? new Vector3f(1.0f, 0.0f, 0.0f)
                    : (ay <= az
                    ? new Vector3f(0.0f, 1.0f, 0.0f)
                    : new Vector3f(0.0f, 0.0f, 1.0f));
            up.set(helper).fma(-helper.dot(direction), direction).normalize();
        }

        previousLightDirection.set(direction);
        previousLightUp.set(up);
        lightBasisValid = true;
        return new Vector3f(up);
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
