/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;

@ModuleInfo(
        id = "breadcrumbs",
        displayName = "Breadcrumbs",
        aliases = {"TrailPath", "PlayerTrail"},
        category = ModuleCategory.VISUALS,
        description = "Renders a persistent fading trail behind the player"
)
public class Breadcrumbs extends Module {

    public enum Mode {
        LINES,
        DOTS,
        BOTH
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> lifetime = num("lifetime", "Lifetime (s)", 15.0, 1.0, 120.0);
    private final EnumValue<Mode> mode = enumSetting("mode", "Mode", Mode.BOTH);
    private final RGBAColorValue color = color("color", "Color", "#8033DDFF");
    private final NumberValue<Double> dotSize = num("dotSize", "Dot Size", 0.08, 0.02, 0.5);

    private final Deque<BreadcrumbPoint> points = new ArrayDeque<>();
    private Vec3 lastPos = null;

    public Breadcrumbs() {
        super();
    }

    @Override
    public void onEnable() {
        points.clear();
        lastPos = null;
    }

    @Override
    public void onDisable() {
        points.clear();
        lastPos = null;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) return;

        Vec3 pos = mc.player.position();
        long now = System.currentTimeMillis();

        if (lastPos == null || pos.distanceToSqr(lastPos) > 0.04) {
            points.addLast(new BreadcrumbPoint(pos, now));
            lastPos = pos;
        }

        long maxAge = (long) (lifetime.get() * 1000.0);
        while (!points.isEmpty() && now - points.peekFirst().time > maxAge) {
            points.pollFirst();
        }
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || mc.player == null || points.size() < 2 || renderer == null) return;

        long now = System.currentTimeMillis();
        long maxAge = (long) (lifetime.get() * 1000.0);
        int baseArgb = color.getArgb();
        int baseA = (baseArgb >>> 24) & 0xFF;
        int r = (baseArgb >>> 16) & 0xFF;
        int g = (baseArgb >>> 8) & 0xFF;
        int b = baseArgb & 0xFF;

        double radius = dotSize.get();
        Mode currentMode = mode.get();

        BreadcrumbPoint prev = null;
        for (BreadcrumbPoint pt : points) {
            long age = now - pt.time;
            if (age >= maxAge) continue;

            float factor = 1.0f - (float) age / (float) maxAge;
            int a = Math.clamp((int) (baseA * factor), 0, 255);
            if (a <= 0) continue;

            if (currentMode == Mode.DOTS || currentMode == Mode.BOTH) {
                AABB box = new AABB(
                        pt.pos.x - radius, pt.pos.y - radius + 0.05, pt.pos.z - radius,
                        pt.pos.x + radius, pt.pos.y + radius + 0.05, pt.pos.z + radius
                );
                addFilledBox(renderer, box, (a << 24) | (r << 16) | (g << 8) | b);
            }

            if ((currentMode == Mode.LINES || currentMode == Mode.BOTH) && prev != null) {
                renderer.line(
                        prev.pos.x, prev.pos.y + 0.05, prev.pos.z,
                        pt.pos.x, pt.pos.y + 0.05, pt.pos.z,
                        r, g, b, a
                );
            }

            prev = pt;
        }
    }

    private static void addFilledBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        if (a <= 0) return;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        renderer.quad(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.maxY, box.minZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.minY, box.minZ, r, g, b, a);
        renderer.quad(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, box.maxX, box.minY, box.maxZ, r, g, b, a);
        renderer.quad(box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, r, g, b, a);
    }

    private record BreadcrumbPoint(Vec3 pos, long time) {}
}
