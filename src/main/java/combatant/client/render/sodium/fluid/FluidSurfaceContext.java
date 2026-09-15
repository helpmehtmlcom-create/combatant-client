/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.sodium.fluid;

import combatant.client.render.engine.material.MaterialClassification;
import combatant.client.render.engine.material.MaterialClassifier;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

/** Exact producer context for Sodium fluid quads; scoped to the meshing thread. */
public final class FluidSurfaceContext {
    private static final ThreadLocal<ArrayDeque<Entry>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private FluidSurfaceContext() {
    }

    public static void push(BlockAndTintGetter level,
                            BlockState blockState,
                            FluidState fluidState,
                            BlockPos worldPos,
                            BlockPos renderOrigin) {
        MaterialClassification classification = MaterialClassifier.classifyFluid(fluidState);
        Vec3 flow = fluidState == null || level == null || worldPos == null
                ? Vec3.ZERO
                : fluidState.getFlow(level, worldPos);
        STACK.get().addLast(new Entry(
                level,
                blockState,
                fluidState,
                worldPos == null ? BlockPos.ZERO : worldPos.immutable(),
                renderOrigin == null ? BlockPos.ZERO : renderOrigin.immutable(),
                flow,
                classification
        ));
    }

    public static void pop() {
        ArrayDeque<Entry> stack = STACK.get();
        if (!stack.isEmpty()) stack.removeLast();
        if (stack.isEmpty()) STACK.remove();
    }

    public static Entry current() {
        ArrayDeque<Entry> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peekLast();
    }

    public record Entry(
            BlockAndTintGetter level,
            BlockState blockState,
            FluidState fluidState,
            BlockPos worldPos,
            BlockPos renderOrigin,
            Vec3 flow,
            MaterialClassification classification
    ) {
    }
}
