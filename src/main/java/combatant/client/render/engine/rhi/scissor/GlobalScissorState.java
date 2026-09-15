/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.scissor;

import com.mojang.blaze3d.systems.RenderPass;

/**
 * Backend-neutral pending scissor for Combatant immediate/RHI submissions.
 *
 * <p>The stored rectangle uses the same framebuffer-space convention that
 * vanilla {@code GuiRenderer} passes to {@link RenderPass#enableScissor}:
 * X is left, Y is already converted from top-left UI coordinates to the
 * framebuffer scissor origin expected by Blaze3D. Do not add a Vulkan-specific
 * second Y conversion here; Blaze3D's RenderPass abstraction owns that backend
 * detail.</p>
 */
public final class GlobalScissorState {
    private static int x;
    private static int y;
    private static int width;
    private static int height;
    private static boolean set;

    private GlobalScissorState() {
    }

    public static void push(int x, int y, int width, int height) {
        if (set) {
            pop();
        }
        if (width <= 0 || height <= 0) {
            GlobalScissorState.x = Math.max(0, x);
            GlobalScissorState.y = Math.max(0, y);
            GlobalScissorState.width = 0;
            GlobalScissorState.height = 0;
            set = true;
            return;
        }
        if (x < 0) {
            width += x;
            x = 0;
        }
        if (y < 0) {
            height += y;
            y = 0;
        }
        GlobalScissorState.x = x;
        GlobalScissorState.y = y;
        GlobalScissorState.width = Math.max(0, width);
        GlobalScissorState.height = Math.max(0, height);
        set = true;
    }

    public static void pop() {
        if (!set) {
            return;
        }
        set = false;
    }

    public static boolean isSet() {
        return set;
    }

    public static Snapshot snapshot() {
        return set ? new Snapshot(x, y, width, height) : null;
    }

    public static void replace(int x, int y, int width, int height) {
        if (set) pop();
        push(x, y, width, height);
    }

    public static boolean applyTo(RenderPass pass, RenderPass.RenderArea renderArea) {
        if (!set || pass == null || renderArea == null) {
            return false;
        }

        int ax1 = renderArea.x();
        int ay1 = renderArea.y();
        int ax2 = ax1 + Math.max(0, renderArea.width());
        int ay2 = ay1 + Math.max(0, renderArea.height());

        int sx1 = Math.max(x, ax1);
        int sy1 = Math.max(y, ay1);
        int sx2 = Math.min(x + Math.max(0, width), ax2);
        int sy2 = Math.min(y + Math.max(0, height), ay2);
        int sw = Math.max(0, sx2 - sx1);
        int sh = Math.max(0, sy2 - sy1);

        pass.enableScissor(sx1, sy1, sw, sh);
        return true;
    }

    public record Snapshot(int x, int y, int width, int height) {
        public int[] rect() {
            return new int[]{x, y, width, height};
        }
    }
}
