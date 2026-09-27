/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.instance;

import combatant.client.render.engine.scene.SceneDrawClass;
import combatant.client.render.engine.scene.lod.SceneLodProfile;
import combatant.client.render.engine.scene.lod.SceneLodSelection;
import combatant.client.render.engine.scene.spatial.SceneSpatialPayload;
import combatant.client.render.engine.scene.spatial.SceneSpatialRegistry;
import combatant.client.render.engine.scene.visibility.SceneViewType;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.Objects;

/**
 * One live world instance of immutable asset data.
 *
 * <p>The instance owns only mutable world state: current/previous transform, spatial membership and
 * per-view visibility/LOD state. Immutable mesh/texture residency remains asset-owned and can be
 * shared by any number of instances.</p>
 */
public final class SceneAssetInstance<T> implements SceneSpatialPayload, AutoCloseable {
    private final SceneInstanceRegistry owner;
    private final long instanceId;
    private final T asset;
    private final AABB localBounds;
    private final SceneDrawClass drawClass;
    private final SceneLodProfile lodProfile;
    private final Matrix4f currentWorld = new Matrix4f();
    private final Matrix4f previousWorld = new Matrix4f();
    private final Matrix4f renderedWorld = new Matrix4f();
    private final MutableViewState[] viewStates = new MutableViewState[SceneViewType.values().length];

    private long spatialHandle;
    private long frameId = Long.MIN_VALUE;
    private AABB worldBounds;
    private float lodBiasStops;
    private boolean spawned;
    private boolean motionCut;
    private boolean pendingSpawn = true;
    private boolean pendingMotionCut = true;
    private boolean mirroredTransform;
    private boolean closed;

    SceneAssetInstance(SceneInstanceRegistry owner,
                       long instanceId,
                       T asset,
                       AABB localBounds,
                       Matrix4fc worldTransform,
                       SceneDrawClass drawClass,
                       SceneLodProfile lodProfile) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.instanceId = instanceId;
        this.asset = Objects.requireNonNull(asset, "asset");
        this.localBounds = requireBounds(localBounds, "localBounds");
        this.drawClass = drawClass == null ? SceneDrawClass.CUSTOM : drawClass;
        this.lodProfile = Objects.requireNonNull(lodProfile, "lodProfile");
        setMatrix(currentWorld, worldTransform);
        previousWorld.set(currentWorld);
        renderedWorld.set(currentWorld);
        mirroredTransform = determinant3x3(currentWorld) < 0.0;
        worldBounds = transformBounds(this.localBounds, currentWorld);
        for (int i = 0; i < viewStates.length; i++) viewStates[i] = new MutableViewState();
    }

    void attach(SceneSpatialRegistry spatial) {
        ensureOpen();
        if (spatialHandle != 0L) throw new IllegalStateException("Scene instance is already spatially attached");
        spatialHandle = spatial.register(this, drawClass, worldBounds);
    }

    void beginFrame(long nextFrameId) {
        ensureOpen();
        if (frameId == nextFrameId) return;
        // previousWorld is the transform actually used by the preceding rendered frame.
        // renderedWorld is updated both for pre-frame producer changes and changes made after
        // beginFrame, so motion does not depend on update ordering relative to frame opening.
        previousWorld.set(renderedWorld);
        renderedWorld.set(currentWorld);
        frameId = nextFrameId;
        spawned = pendingSpawn;
        motionCut = pendingMotionCut || spawned;
        pendingSpawn = false;
        pendingMotionCut = false;
        for (MutableViewState state : viewStates) state.beginFrame(nextFrameId);
    }

    void endFrame(long completedFrameId) {
        if (frameId != completedFrameId) return;
        spawned = false;
        motionCut = false;
    }

    boolean setWorldTransform(Matrix4fc transform, boolean discontinuity) {
        ensureOpen();
        Matrix4f next = new Matrix4f();
        setMatrix(next, transform);
        if (sameMatrix(currentWorld, next)) {
            if (discontinuity) {
                previousWorld.set(currentWorld);
                renderedWorld.set(currentWorld);
                markMotionCut();
            }
            return false;
        }
        currentWorld.set(next);
        mirroredTransform = determinant3x3(currentWorld) < 0.0;
        if (owner.frameOpen()) renderedWorld.set(currentWorld);
        if (discontinuity || spawned || pendingSpawn) {
            previousWorld.set(currentWorld);
            renderedWorld.set(currentWorld);
            if (discontinuity) markMotionCut();
        }
        worldBounds = transformBounds(localBounds, currentWorld);
        owner.onTransformChanged(this, discontinuity);
        return true;
    }

    void markVisible(SceneViewType type, long frameId, SceneLodSelection selection) {
        MutableViewState state = viewStates[type.ordinal()];
        state.frameId = frameId;
        state.visible = true;
        if (selection != null) {
            state.lodLevel = selection.level();
            state.projectedDiameterPixels = selection.projectedDiameterPixels();
            state.effectiveDiameterPixels = selection.effectiveDiameterPixels();
        }
    }

    int previousLod(SceneViewType type) {
        return viewStates[type.ordinal()].lodLevel;
    }

    void detachSpatial(SceneSpatialRegistry spatial) {
        if (spatialHandle == 0L) return;
        spatial.unregister(spatialHandle);
        spatialHandle = 0L;
    }

    void disposeWithoutOwnerCallback(SceneSpatialRegistry spatial) {
        if (closed) return;
        detachSpatial(spatial);
        closed = true;
    }

    public long instanceId() { return instanceId; }
    public T asset() { return asset; }
    public AABB localBounds() { return localBounds; }
    public AABB worldBounds() { return worldBounds; }
    public SceneDrawClass drawClass() { return drawClass; }
    public SceneLodProfile lodProfile() { return lodProfile; }
    public long spatialHandle() { return spatialHandle; }
    public long frameId() { return frameId; }
    public boolean spawned() { return spawned; }
    public boolean motionCut() { return motionCut; }
    public boolean mirroredTransform() { return mirroredTransform; }
    public boolean isClosed() { return closed; }
    public float lodBiasStops() { return lodBiasStops; }

    public void setLodBiasStops(float lodBiasStops) {
        ensureOpen();
        this.lodBiasStops = Float.isFinite(lodBiasStops)
                ? Math.max(-16.0f, Math.min(16.0f, lodBiasStops)) : 0.0f;
    }

    public Matrix4f currentWorldTransform() { return new Matrix4f(currentWorld); }
    public Matrix4f previousWorldTransform() { return new Matrix4f(previousWorld); }

    public boolean setWorldTransform(Matrix4fc transform) {
        return setWorldTransform(transform, false);
    }

    /** Updates transform and explicitly suppresses temporal motion across the discontinuity. */
    public boolean teleport(Matrix4fc transform) {
        return setWorldTransform(transform, true);
    }

    public SceneInstanceViewState viewState(SceneViewType type) {
        Objects.requireNonNull(type, "type");
        return viewStates[type.ordinal()].snapshot();
    }

    @Override
    public void close() {
        if (closed) return;
        owner.unregister(this);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Scene asset instance is closed");
    }

    private void markMotionCut() {
        if (owner.frameOpen() && frameId == owner.frameId()) motionCut = true;
        else pendingMotionCut = true;
    }

    private static void setMatrix(Matrix4f target, Matrix4fc source) {
        if (source == null) target.identity();
        else target.set(source);
        if (!finite(target)) throw new IllegalArgumentException("World transform contains non-finite values");
    }


    private static boolean sameMatrix(Matrix4fc a, Matrix4fc b) {
        return a.m00() == b.m00() && a.m01() == b.m01() && a.m02() == b.m02() && a.m03() == b.m03()
                && a.m10() == b.m10() && a.m11() == b.m11() && a.m12() == b.m12() && a.m13() == b.m13()
                && a.m20() == b.m20() && a.m21() == b.m21() && a.m22() == b.m22() && a.m23() == b.m23()
                && a.m30() == b.m30() && a.m31() == b.m31() && a.m32() == b.m32() && a.m33() == b.m33();
    }

    private static double determinant3x3(Matrix4fc m) {
        return (double) m.m00() * ((double) m.m11() * m.m22() - (double) m.m21() * m.m12())
                - (double) m.m10() * ((double) m.m01() * m.m22() - (double) m.m21() * m.m02())
                + (double) m.m20() * ((double) m.m01() * m.m12() - (double) m.m11() * m.m02());
    }

    private static boolean finite(Matrix4fc matrix) {
        return Float.isFinite(matrix.m00()) && Float.isFinite(matrix.m01()) && Float.isFinite(matrix.m02()) && Float.isFinite(matrix.m03())
                && Float.isFinite(matrix.m10()) && Float.isFinite(matrix.m11()) && Float.isFinite(matrix.m12()) && Float.isFinite(matrix.m13())
                && Float.isFinite(matrix.m20()) && Float.isFinite(matrix.m21()) && Float.isFinite(matrix.m22()) && Float.isFinite(matrix.m23())
                && Float.isFinite(matrix.m30()) && Float.isFinite(matrix.m31()) && Float.isFinite(matrix.m32()) && Float.isFinite(matrix.m33());
    }

    private static AABB requireBounds(AABB bounds, String label) {
        if (bounds == null) throw new IllegalArgumentException(label + " is required");
        double[] values = {bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ};
        for (double value : values) if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " contains non-finite values");
        if (bounds.minX > bounds.maxX || bounds.minY > bounds.maxY || bounds.minZ > bounds.maxZ) {
            throw new IllegalArgumentException(label + " is inverted");
        }
        return bounds;
    }

    /** Allocation-free 8-corner affine AABB transform. */
    private static AABB transformBounds(AABB box, Matrix4fc m) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? box.minX : box.maxX;
            double y = (corner & 2) == 0 ? box.minY : box.maxY;
            double z = (corner & 4) == 0 ? box.minZ : box.maxZ;
            double tx = m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
            double ty = m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
            double tz = m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
            double tw = m.m03() * x + m.m13() * y + m.m23() * z + m.m33();
            if (tw != 0.0 && tw != 1.0) {
                tx /= tw; ty /= tw; tz /= tw;
            }
            minX = Math.min(minX, tx); minY = Math.min(minY, ty); minZ = Math.min(minZ, tz);
            maxX = Math.max(maxX, tx); maxY = Math.max(maxY, ty); maxZ = Math.max(maxZ, tz);
        }
        return requireBounds(new AABB(minX, minY, minZ, maxX, maxY, maxZ), "worldBounds");
    }

    private static final class MutableViewState {
        long frameId = Long.MIN_VALUE;
        boolean visible;
        int lodLevel = -1;
        float projectedDiameterPixels;
        float effectiveDiameterPixels;

        void beginFrame(long nextFrameId) {
            if (frameId == nextFrameId) return;
            frameId = nextFrameId;
            visible = false;
        }

        SceneInstanceViewState snapshot() {
            return new SceneInstanceViewState(frameId, visible, lodLevel, projectedDiameterPixels, effectiveDiameterPixels);
        }
    }
}
