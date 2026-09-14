/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.framegraph.FrameGraphResourceKind;
import combatant.client.render.engine.framegraph.FrameGraphResourceLifetime;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageBuffer;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;

/** Non-owning bindings of logical graph resources to current-frame RHI objects. */
public final class DeferredResourceBindings {
    private final EnumMap<DeferredResource, GpuTextureView> textures = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, RhiStorageBuffer> buffers = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, RhiStorageImage> images = new EnumMap<>(DeferredResource.class);
    private final EnumMap<DeferredResource, DeferredResourceAllocator.Allocation> owned =
            new EnumMap<>(DeferredResource.class);
    private final DeferredResourceAllocator allocator;
    private long frameId = Long.MIN_VALUE;
    private long historyEpoch = Long.MIN_VALUE;

    public DeferredResourceBindings(DeferredResourceAllocator allocator) {
        if (allocator == null) throw new IllegalArgumentException("allocator");
        this.allocator = allocator;
    }

    /** Standalone bindings are useful for graph validation/tests and never allocate implicitly. */
    public DeferredResourceBindings() {
        this.allocator = null;
    }

    public void beginFrame(long frameId) {
        beginFrame(frameId, historyEpoch);
    }

    public void beginFrame(long frameId, long historyEpoch) {
        if (this.frameId == frameId && this.historyEpoch == historyEpoch) return;
        this.frameId = frameId;
        this.historyEpoch = historyEpoch;
        if (allocator != null) allocator.beginFrame(frameId);
        clearBindings();
    }

    /** Synchronizes persistent-resource validity after a mid-frame history invalidation. */
    public void setHistoryEpoch(long historyEpoch) {
        if (this.historyEpoch == historyEpoch) return;
        this.historyEpoch = historyEpoch;
        for (DeferredResource resource : DeferredResource.values()) {
            if (resource.key().lifetime() != FrameGraphResourceLifetime.PERSISTENT) continue;
            textures.remove(resource);
            buffers.remove(resource);
            images.remove(resource);
            owned.remove(resource);
        }
    }

    /** Drops every non-owning logical binding after runtime/device/world teardown. */
    public void reset() {
        frameId = Long.MIN_VALUE;
        historyEpoch = Long.MIN_VALUE;
        clearBindings();
    }

    private void clearBindings() {
        textures.clear();
        buffers.clear();
        images.clear();
        owned.clear();
    }

    public void bindTexture(DeferredResource resource, @Nullable GpuTextureView view) {
        requireTextureResource(resource);
        if (view == null) textures.remove(resource); else textures.put(resource, view);
    }

    public void bindBuffer(DeferredResource resource, @Nullable RhiStorageBuffer buffer) {
        if (resource == null || resource.key().kind() != FrameGraphResourceKind.BUFFER) {
            throw new IllegalArgumentException("Not a buffer resource: " + resource);
        }
        if (buffer == null) buffers.remove(resource); else buffers.put(resource, buffer);
    }

    public void bindStorageImage(DeferredResource resource, @Nullable RhiStorageImage image) {
        requireTextureResource(resource);
        if (image == null) {
            images.remove(resource);
        } else {
            images.put(resource, image);
            textures.putIfAbsent(resource, image.view());
        }
    }

    public @Nullable GpuTextureView texture(DeferredResource resource) {
        return textures.get(resource);
    }

    public @Nullable RhiStorageBuffer buffer(DeferredResource resource) {
        return buffers.get(resource);
    }

    public @Nullable RhiStorageImage storageImage(DeferredResource resource) {
        return images.get(resource);
    }

    public boolean isBound(DeferredResource resource) {
        return textures.containsKey(resource) || buffers.containsKey(resource) || images.containsKey(resource);
    }

    /** True when the resource contains defined data for this frame/history generation. */
    public boolean isValid(DeferredResource resource) {
        DeferredResourceAllocator.Allocation allocation = owned.get(resource);
        if (allocation != null) return allocationValid(resource, allocation);
        if (allocator != null) {
            DeferredResourceAllocator.Allocation persistent = allocator.current(resource);
            if (persistent != null) return allocationValid(resource, persistent);
        }
        return isBound(resource);
    }

    /**
     * Rebinds an already-owned allocation without creating it. This is primarily used for
     * persistent history resources after beginFrame() clears the frame-local logical bindings.
     */
    public boolean bindExisting(DeferredResource resource) {
        return bindExisting(resource, DeferredRuntimeConfig.current());
    }

    /**
     * Rebinds persistent history only when its physical shape still matches the current frame
     * policy. A runtime quality change therefore invalidates history rather than exposing a stale
     * allocation with the old scale/mip contract to a temporal consumer.
     */
    public boolean bindExisting(DeferredResource resource, DeferredRuntimeConfig.Snapshot settings) {
        if (isBound(resource)) return isValid(resource);
        if (allocator == null) return false;
        DeferredResourceAllocator.Allocation allocation = allocator.current(resource);
        if (allocation == null || !matchesCurrentPolicy(resource, allocation, settings)
                || !allocationValid(resource, allocation)) return false;
        owned.put(resource, allocation);
        bindTexture(resource, allocation.view());
        bindStorageImage(resource, allocation.storageImage());
        return allocation.valid();
    }

    public void markWritten(DeferredResource resource) {
        DeferredResourceAllocator.Allocation allocation = owned.get(resource);
        if (allocation == null) return;
        if (resource.key().lifetime() == FrameGraphResourceLifetime.PERSISTENT) {
            allocation.markValid(historyEpoch);
        } else {
            allocation.markValid();
        }
    }

    public void ensureTexture(DeferredResource resource, CombatantRhi rhi) {
        ensureTexture(resource, rhi, DeferredRuntimeConfig.current());
    }

    public void ensureTexture(DeferredResource resource, CombatantRhi rhi, DeferredRuntimeConfig.Snapshot settings) {
        if (isBound(resource)) return;
        if (allocator == null) {
            throw new IllegalStateException("No deferred resource allocator is attached");
        }
        GpuTextureView reference = textures.get(DeferredResource.SCENE_COLOR);
        if (reference == null) reference = textures.get(DeferredResource.MAIN_DEPTH);
        if (reference == null) {
            throw new IllegalStateException("Cannot size " + resource + " without scene color/depth");
        }
        int samples = reference.texture() instanceof combatant.client.mixininterface.IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;
        DeferredResourceAllocator.Allocation allocation = allocator.acquire(
                resource, reference.getWidth(0), reference.getHeight(0), samples, rhi, settings
        );
        owned.put(resource, allocation);
        bindTexture(resource, allocation.view());
        bindStorageImage(resource, allocation.storageImage());
    }


    private boolean matchesCurrentPolicy(DeferredResource resource,
                                         DeferredResourceAllocator.Allocation allocation,
                                         DeferredRuntimeConfig.Snapshot settings) {
        DeferredTextureSpec spec = resource == null ? null : resource.textureSpec();
        if (spec == null) return true;
        GpuTextureView reference = textures.get(DeferredResource.SCENE_COLOR);
        if (reference == null) reference = textures.get(DeferredResource.MAIN_DEPTH);
        if (reference == null) return false;

        int width = spec.resolution().width(reference.getWidth(0), settings);
        int height = spec.resolution().height(reference.getHeight(0), settings);
        int sceneSamples = reference.texture() instanceof combatant.client.mixininterface.IMsaaTexture msaa
                ? Math.max(1, msaa.combatant$getSamples()) : 1;
        int samples = spec.samples() == DeferredTextureSpec.SamplePolicy.MATCH_SCENE ? sceneSamples : 1;
        int mipLevels = spec.mipChain()
                ? 32 - Integer.numberOfLeadingZeros(Math.max(width, height)) : 1;
        if (resource == DeferredResource.DEPTH_PYRAMID && settings != null
                && settings.depthPyramidMaxMipLevels() > 0) {
            mipLevels = Math.min(mipLevels, settings.depthPyramidMaxMipLevels());
        }
        return allocation.width() == width
                && allocation.height() == height
                && allocation.samples() == samples
                && allocation.mipLevels() == mipLevels;
    }

    private boolean allocationValid(DeferredResource resource, DeferredResourceAllocator.Allocation allocation) {
        if (resource != null && resource.key().lifetime() == FrameGraphResourceLifetime.PERSISTENT) {
            return allocation.validForEpoch(historyEpoch);
        }
        return allocation.valid();
    }

    public long frameId() {
        return frameId;
    }

    public long historyEpoch() {
        return historyEpoch;
    }

    private static void requireTextureResource(DeferredResource resource) {
        if (resource == null || resource.key().kind() == FrameGraphResourceKind.BUFFER) {
            throw new IllegalArgumentException("Not a texture/external resource: " + resource);
        }
    }
}
