/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RenderWorldEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.render.engine.renderer.Renderer3D;

import java.util.HashSet;
import java.util.Set;

@ModuleInfo(
        id = "portalesp",
        displayName = "PortalESP",
        category = ModuleCategory.VISUALS
)
public final class PortalESP extends Module {

    private final NumberValue<Integer> radius = num("radius", 32, 8, 64);
    private final RGBAColorValue netherColor = color("nether_color", "#B28000FF");
    private final RGBAColorValue endColor = color("end_color", "#B200FFFF");
    private final BooleanValue box = bool("box", true);
    private final BooleanValue outline = bool("outline", true);

    private final Minecraft mc = Minecraft.getInstance();
    private final Set<BlockPos> portals = new HashSet<>();
    private int ticks = 0;

    @Override
    public void onDisable() {
        portals.clear();
    }

    @EventHandler
    public void onRender(RenderWorldEvent event) {
        if (!isEnabled() || mc.level == null || mc.player == null) return;

        ticks++;
        if (ticks % 20 == 0) {
            portals.clear();
            BlockPos p = mc.player.blockPosition();
            int r = radius.get();
            for (int x = -r; x <= r; x++) {
                for (int y = -r / 2; y <= r / 2; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos target = p.offset(x, y, z);
                        BlockState state = mc.level.getBlockState(target);
                        if (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)) {
                            portals.add(target);
                        }
                    }
                }
            }
        }

        for (BlockPos pos : portals) {
            BlockState state = mc.level.getBlockState(pos);
            int c = state.is(Blocks.NETHER_PORTAL) ? netherColor.getRGB() : endColor.getRGB();
            AABB bb = new AABB(pos);
            if (box.get()) {
                Renderer3D.drawBox(bb, c);
            }
            if (outline.get()) {
                Renderer3D.drawBoundingBox(bb, 1.5f, c);
            }
        }
    }
}
