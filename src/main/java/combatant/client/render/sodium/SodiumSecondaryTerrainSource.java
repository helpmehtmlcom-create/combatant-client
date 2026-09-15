/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import combatant.client.mixins.sodium.SodiumRenderSectionManagerAccessor;
import combatant.client.mixins.sodium.SodiumSortedRenderListsInvoker;
import combatant.client.render.engine.deferred.DeferredSecondaryView;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.SectionStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Builds view-local Sodium terrain lists from already uploaded section storage.
 *
 * <p>Sodium 0.9.1 keeps the storage behind {@link SectionStorage}; the concrete
 * {@code QueuedSectionStorage.sections} map is exposed only by the versioned compat mixin.
 * No deferred producer is allowed to know that private field name.</p>
 */
public final class SodiumSecondaryTerrainSource {
    private static final float SECTION_EXTENT = 16.0f;
    private static final float CULL_PADDING = 1.0f;

    private SodiumSecondaryTerrainSource() {
    }

    public static SortedRenderLists buildRenderLists(RenderSectionManager manager, DeferredSecondaryView view) {
        if (manager == null) throw new IllegalArgumentException("manager");
        if (view == null) throw new IllegalArgumentException("view");

        Matrix4f viewProjection = view.viewProjection();
        FrustumIntersection frustum = new FrustumIntersection(viewProjection);
        Map<RenderRegion, ChunkRenderList> byRegion = new IdentityHashMap<>();
        SectionStorage storage = ((SodiumRenderSectionManagerAccessor) manager).combatant$getRenderSections();
        if (!(storage instanceof SodiumSectionStorageView storageView)) {
            throw new IllegalStateException("Unsupported Sodium section storage: "
                    + (storage == null ? "null" : storage.getClass().getName()));
        }

        double cameraX = view.origin().x;
        double cameraY = view.origin().y;
        double cameraZ = view.origin().z;

        for (RenderSection section : storageView.combatant$sections()) {
            if (section == null || section.isDisposed() || !section.isBuilt()) continue;

            float minX = (float) (section.getOriginX() - cameraX) - CULL_PADDING;
            float minY = (float) (section.getOriginY() - cameraY) - CULL_PADDING;
            float minZ = (float) (section.getOriginZ() - cameraZ) - CULL_PADDING;
            float maxX = minX + SECTION_EXTENT + CULL_PADDING * 2.0f;
            float maxY = minY + SECTION_EXTENT + CULL_PADDING * 2.0f;
            float maxZ = minZ + SECTION_EXTENT + CULL_PADDING * 2.0f;
            if (!frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ)) continue;

            RenderRegion region = section.getRegion();
            ChunkRenderList list = byRegion.computeIfAbsent(region, ChunkRenderList::new);
            list.add(section.getSectionIndex());
        }

        ArrayList<ChunkRenderList> ordered = new ArrayList<>(byRegion.values());
        ordered.sort(Comparator
                .comparingInt((ChunkRenderList list) -> list.getRegion().getChunkY())
                .thenComparingInt(list -> list.getRegion().getChunkZ())
                .thenComparingInt(list -> list.getRegion().getChunkX()));
        ObjectArrayList<ChunkRenderList> fast = new ObjectArrayList<>(ordered.size());
        fast.addAll(ordered);
        SortedRenderLists lists = SodiumSortedRenderListsInvoker.combatant$create(fast);

        // MultiDrawBatch is cached by RenderRegion + TerrainRenderPass, not by camera. Invalidate
        // before the first secondary draw and again when the secondary scope closes.
        clearCachedBatches(lists);
        return lists;
    }

    public static void clearCachedBatches(SortedRenderLists lists) {
        if (lists == null) return;
        var iterator = lists.iterator(false);
        while (iterator.hasNext()) {
            RenderRegion region = iterator.next().getRegion();
            region.clearCachedBatchFor(DefaultTerrainRenderPasses.SOLID);
            region.clearCachedBatchFor(DefaultTerrainRenderPasses.CUTOUT);
        }
    }
}
