/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.scene.spatial;

import combatant.client.render.engine.scene.SceneDrawClass;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.world.phys.AABB;

/**
 * Stable handle-backed record stored in the shared spatial registry.
 * Bounds may change when a world instance moves; payload identity remains stable.
 */
public final class SceneSpatialRecord {
    private final long handle;
    private final SceneSpatialPayload payload;
    private final SceneDrawClass drawClass;
    private AABB bounds;
    private SceneBoundingSphere sphere;
    final LongArrayList sectionKeys = new LongArrayList();
    boolean largeObject;

    SceneSpatialRecord(long handle, SceneSpatialPayload payload, SceneDrawClass drawClass, AABB bounds) {
        this.handle = handle;
        this.payload = payload;
        this.drawClass = drawClass == null ? SceneDrawClass.CUSTOM : drawClass;
        setBounds(bounds);
    }

    void setBounds(AABB bounds) {
        if (bounds == null) throw new IllegalArgumentException("Spatial bounds are required");
        if (!finite(bounds.minX) || !finite(bounds.minY) || !finite(bounds.minZ)
                || !finite(bounds.maxX) || !finite(bounds.maxY) || !finite(bounds.maxZ)) {
            throw new IllegalArgumentException("Spatial bounds contain non-finite coordinates");
        }
        if (bounds.minX > bounds.maxX || bounds.minY > bounds.maxY || bounds.minZ > bounds.maxZ) {
            throw new IllegalArgumentException("Spatial bounds are inverted");
        }
        this.bounds = bounds;
        this.sphere = SceneBoundingSphere.from(bounds);
    }

    private static boolean finite(double value) {
        return Double.isFinite(value);
    }

    public long handle() { return handle; }
    public SceneSpatialPayload payload() { return payload; }
    public SceneDrawClass drawClass() { return drawClass; }
    public AABB bounds() { return bounds; }
    public SceneBoundingSphere sphere() { return sphere; }
    public boolean largeObject() { return largeObject; }
    public int sectionCount() { return sectionKeys.size(); }
}
