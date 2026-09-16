/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.deferred;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiStorageImage;
import combatant.client.render.engine.rhi.shader.StorageAccess;
import combatant.client.render.engine.rhi.shader.StorageImageDescriptor;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import combatant.client.render.engine.framegraph.FrameGraphResourceLifetime;

/**
 * Lazy physical owner for optional deferred resources.
 *
 * <p>Logical transient resources remain allocated across frames and only lose their contents.
 * This avoids steady-state texture creation while still allowing a future aliasing planner to
 * map multiple non-overlapping logical lifetimes onto one physical allocation.</p>
 */
public final class DeferredResourceAllocator implements AutoCloseable {
    private final EnumMap<DeferredResource, Allocation> allocations = new EnumMap<>(DeferredResource.class);
    private @Nullable CombatantRhi owner;
    private long frameId = Long.MIN_VALUE;

    public void beginFrame(long frameId) {
        if (this.frameId == frameId) return;
        this.frameId = frameId;
        for (var entry : allocations.entrySet()) {
            if (entry.getKey().key().lifetime() == FrameGraphResourceLifetime.TRANSIENT) {
                entry.getValue().invalidate();
            }
        }
    }

    public Allocation acquire(DeferredResource resource,
                              int fullWidth,
                              int fullHeight,
                              int sceneSamples,
                              CombatantRhi rhi,
                              DeferredRuntimeConfig.Snapshot settings) {
        if (resource == null || resource.textureSpec() == null) {
            throw new IllegalArgumentException("Deferred resource has no physical texture spec: " + resource);
        }
        if (rhi == null) throw new IllegalArgumentException("rhi");
        RenderSystem.assertOnRenderThread();

        if (owner != null && owner != rhi) {
            closeImmediately();
        }
        owner = rhi;

        DeferredTextureSpec spec = resource.textureSpec();
        int width = spec.width(fullWidth, settings);
        int height = spec.height(fullHeight, settings);
        int samples = spec.samples() == DeferredTextureSpec.SamplePolicy.MATCH_SCENE
                ? Math.max(1, sceneSamples) : 1;
        int mipLevels = spec.mipChain()
                ? 32 - Integer.numberOfLeadingZeros(Math.max(width, height)) : 1;
        if (resource == DeferredResource.DEPTH_PYRAMID && settings != null
                && settings.depthPyramidMaxMipLevels() > 0) {
            mipLevels = Math.min(mipLevels, settings.depthPyramidMaxMipLevels());
        }
        if (samples > 1 && mipLevels > 1) {
            throw new IllegalStateException("Multisampled deferred resources cannot have mip chains: " + resource);
        }
        AllocationKey key = new AllocationKey(width, height, samples, mipLevels, spec);
        Allocation current = allocations.get(resource);
        if (current != null && current.key.equals(key)) return current;
        if (current != null) retire(current);

        Allocation created = create(resource, key, rhi);
        allocations.put(resource, created);
        return created;
    }

    public @Nullable Allocation current(DeferredResource resource) {
        return allocations.get(resource);
    }

    /** Invalidates size-dependent and temporal contents without forcing synchronous GPU destruction. */
    public void reset() {
        for (Allocation allocation : allocations.values()) retire(allocation);
        allocations.clear();
    }

    @Override
    public void close() {
        closeImmediately();
    }

    private static Allocation create(DeferredResource resource, AllocationKey key, CombatantRhi rhi) {
        String label = "combatant-" + resource.key().name().replace('.', '-');
        DeferredTextureSpec spec = key.spec;
        if (spec.storageImage()) {
            RhiStorageImage image = rhi.advancedShaders().createStorageImage(new StorageImageDescriptor(
                    label, key.width, key.height, spec.format(), StorageAccess.READ_WRITE,
                    true, spec.renderAttachment(), key.mipLevels
            ));
            return new Allocation(key, image.view(), image, image);
        }

        int usage = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING;
        if (spec.renderAttachment()) usage |= GpuTexture.USAGE_RENDER_ATTACHMENT;
        GpuTexture texture = key.samples > 1
                ? rhi.msaa().createTexture(label, usage, spec.format(), key.width, key.height, key.samples)
                : RenderSystem.getDevice().createTexture(
                        label, usage, spec.format(), key.width, key.height, 1, key.mipLevels
                );
        GpuTextureView view = null;
        try {
            view = RenderSystem.getDevice().createTextureView(texture);
            return new Allocation(key, view, null, new OwnedTexture(texture, view));
        } catch (RuntimeException | Error t) {
            if (view != null) view.close();
            texture.close();
            throw t;
        }
    }

    private void retire(Allocation allocation) {
        CombatantRhi currentOwner = owner;
        if (currentOwner == null) {
            allocation.close();
            return;
        }
        currentOwner.resources().retire(allocation);
    }

    private void closeImmediately() {
        for (Allocation allocation : allocations.values()) allocation.close();
        allocations.clear();
        owner = null;
        frameId = Long.MIN_VALUE;
    }

    private record AllocationKey(
            int width,
            int height,
            int samples,
            int mipLevels,
            DeferredTextureSpec spec
    ) {
    }

    public static final class Allocation implements AutoCloseable {
        private final AllocationKey key;
        private final GpuTextureView view;
        private final @Nullable RhiStorageImage storageImage;
        private final AutoCloseable owned;
        private boolean closed;
        private boolean valid;
        private long validEpoch = Long.MIN_VALUE;

        private Allocation(AllocationKey key,
                           GpuTextureView view,
                           @Nullable RhiStorageImage storageImage,
                           AutoCloseable owned) {
            this.key = key;
            this.view = view;
            this.storageImage = storageImage;
            this.owned = owned;
        }

        public GpuTextureView view() {
            if (closed) throw new IllegalStateException("Deferred allocation is closed");
            return view;
        }

        public @Nullable RhiStorageImage storageImage() {
            if (closed) throw new IllegalStateException("Deferred allocation is closed");
            return storageImage;
        }

        public int width() {
            return key.width;
        }

        public int height() {
            return key.height;
        }

        public int samples() {
            return key.samples;
        }

        public int mipLevels() {
            return key.mipLevels;
        }

        public boolean valid() {
            return !closed && valid;
        }

        public boolean validForEpoch(long epoch) {
            return !closed && valid && validEpoch == epoch;
        }

        public void markValid() {
            markValid(Long.MIN_VALUE);
        }

        public void markValid(long epoch) {
            if (closed) throw new IllegalStateException("Deferred allocation is closed");
            valid = true;
            validEpoch = epoch;
        }

        private void invalidate() {
            valid = false;
            validEpoch = Long.MIN_VALUE;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                owned.close();
            } catch (RuntimeException | Error t) {
                throw t;
            } catch (Exception t) {
                throw new IllegalStateException("Failed to close deferred allocation", t);
            }
        }
    }

    private static final class OwnedTexture implements AutoCloseable {
        private final GpuTexture texture;
        private final GpuTextureView view;

        private OwnedTexture(GpuTexture texture, GpuTextureView view) {
            this.texture = texture;
            this.view = view;
        }

        @Override
        public void close() {
            view.close();
            texture.close();
        }
    }
}
