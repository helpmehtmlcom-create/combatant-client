/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.helpers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import combatant.client.features.module.modules.visuals.NoRender;
import combatant.client.render.engine.material.MaterialClassification;
import combatant.client.render.engine.material.MaterialClassifier;
import combatant.client.render.engine.material.MaterialDomain;
import combatant.client.util.logging.DebugLog;

import java.util.ArrayDeque;

public enum SodiumSurfaceFlagContext {
    ;
    private static final ThreadLocal<ArrayDeque<StateFlags>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile boolean loggedSoftFadeState;

    public static void pushForState(Object stateObj) {
        pushForState(stateObj, null, null);
    }

    public static void pushForState(Object stateObj, BlockPos origin) {
        pushForState(stateObj, null, origin);
    }

    public static void pushForState(Object stateObj, BlockPos worldPos, BlockPos origin) {
        boolean softFade = false;
        BlockState blockState = null;
        if (stateObj instanceof BlockState state) {
            softFade = NoRender.shouldFadeSoftBlock(state);
            blockState = state;
            if (softFade && !loggedSoftFadeState) {
                loggedSoftFadeState = true;
                DebugLog.renderThread("[SodiumSurface] state hook saw soft fade block: %s", state.getBlock());
            }
        }
        STACK.get().addLast(new StateFlags(
                softFade, blockState,
                worldPos == null ? null : worldPos.immutable(),
                origin == null ? null : origin.immutable()
        ));
    }

    public static void pop() {
        ArrayDeque<StateFlags> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.removeLast();
        }
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    public static int getSurfaceFlags() {
        return getSurfaceFlags(0.0f);
    }

    public static int getSurfaceFlags(float vertexY) {
        StateFlags flags = current();
        int encoded = 0;
        if (flags.softFade) {
            encoded |= SodiumMaterialFlags.SURFACE_FLAG_SOFT_FADE;
        }
        return encoded;
    }

    public static MaterialClassification materialClassification(MaterialDomain fallback) {
        StateFlags flags = current();
        return MaterialClassifier.classifyBlock(flags.blockState, fallback);
    }

    public static BlockPos worldPos() {
        return current().worldPos;
    }

    public static BlockPos renderOrigin() {
        return current().renderOrigin;
    }

    private static StateFlags current() {
        ArrayDeque<StateFlags> stack = STACK.get();
        return stack.isEmpty() ? StateFlags.EMPTY : stack.peekLast();
    }

    private record StateFlags(boolean softFade, BlockState blockState,
                              BlockPos worldPos, BlockPos renderOrigin) {
        private static final StateFlags EMPTY = new StateFlags(false, null, null, null);
    }
}
