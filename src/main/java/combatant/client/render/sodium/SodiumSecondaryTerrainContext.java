/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.sodium;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import combatant.client.render.engine.deferred.DeferredSecondaryView;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Render-thread scoped override used by Combatant secondary world views.
 *
 * <p>The scope owns only view-local submission state. It never mutates Minecraft's primary
 * camera or Sodium's primary visibility tree. Purpose is explicit so one secondary visibility
 * implementation can feed shadow, reflection and future capture producers without shader-state
 * guessing.</p>
 */
public final class SodiumSecondaryTerrainContext {
    public enum Purpose {
        SHADOW_DEPTH,
        LOCAL_LIGHT_SHADOW,
        REFLECTION_CAPTURE
    }

    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private SodiumSecondaryTerrainContext() {
    }

    public static State current() {
        return CURRENT.get();
    }

    public static boolean active() {
        return CURRENT.get() != null;
    }

    public static void run(Purpose purpose, DeferredSecondaryView view, TextureTarget target, Runnable action) {
        if (purpose == null) throw new IllegalArgumentException("purpose");
        if (view == null) throw new IllegalArgumentException("view");
        if (target == null) throw new IllegalArgumentException("target");
        if (action == null) throw new IllegalArgumentException("action");
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Nested Sodium secondary terrain render is not supported");
        }

        State state = new State(purpose, view, target);
        CURRENT.set(state);
        try {
            action.run();
        } finally {
            try {
                state.releaseCachedBatches();
            } finally {
                CURRENT.remove();
            }
        }
    }

    /** Backward-compatible shadow scope for existing callers. */
    public static void run(DeferredSecondaryView view, TextureTarget target, Runnable action) {
        run(Purpose.SHADOW_DEPTH, view, target, action);
    }

    public static final class State {
        private final Purpose purpose;
        private final DeferredSecondaryView view;
        private final TextureTarget target;
        private SortedRenderLists renderLists;

        private State(Purpose purpose, DeferredSecondaryView view, TextureTarget target) {
            this.purpose = purpose;
            this.view = view;
            this.target = target;
        }

        public Purpose purpose() {
            return purpose;
        }

        public DeferredSecondaryView view() {
            return view;
        }

        public SortedRenderLists renderLists(RenderSectionManager manager) {
            if (renderLists == null) {
                renderLists = SodiumSecondaryTerrainSource.buildRenderLists(manager, view);
            }
            return renderLists;
        }

        public RenderPass openPass(CommandEncoder encoder, Supplier<String> label) {
            if (!view.hasExplicitViewport()) {
                throw new IllegalStateException("Secondary terrain view has no explicit atlas viewport: " + view.id());
            }
            RenderPassDescriptor descriptor = RenderPassDescriptor.create(label)
                    .withColorAttachment(target.getColorTextureView(), Optional.empty())
                    .withDepthAttachment(target.getDepthTextureView(), OptionalDouble.empty())
                    .withRenderArea(new RenderPass.RenderArea(
                            view.viewportX(), view.viewportY(), view.viewportWidth(), view.viewportHeight()
                    ));
            return encoder.createRenderPass(descriptor);
        }

        public void releaseCachedBatches() {
            if (renderLists == null) return;
            SodiumSecondaryTerrainSource.clearCachedBatches(renderLists);
        }
    }
}
