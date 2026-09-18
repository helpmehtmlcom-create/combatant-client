/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * DonutSMP server region map tracker and boundary notifier.
 * Ported and adapted from 67Client's RegionMapModule.
 */
@ModuleInfo(
        id = "regionmap",
        displayName = "RegionMap",
        description = "Tracks DonutSMP 50,000-block server regions (EU, NA, Asia, Oce) and boundary crossings.",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"donutsmpmap", "serverregions", "regionboundary"}
)
public class RegionMap extends Module {

    public static final double REGION_SIZE = 50_000.0;
    private static final String[] REGION_NAMES = {"EU-C", "EU-W", "NA-E", "NA-W", "Asia", "Oce"};

    private final BooleanValue boundaryAlert =
            bool("regionmap_boundary_alert", "boundary_alert", true);
    private final BooleanValue chatNotice =
            bool("regionmap_chat_notice", "chat_notice", true);

    private final Minecraft mc = Minecraft.getInstance();
    private String lastRegion = null;

    @Override
    public void onEnable() {
        lastRegion = null;
    }

    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (mc.player == null) return;

        double x = mc.player.getX();
        double z = mc.player.getZ();
        String currentRegion = calculateRegion(x, z);

        if (lastRegion == null) {
            lastRegion = currentRegion;
            if (chatNotice.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal("§d[RegionMap] §7Current DonutSMP Region: §e" + currentRegion)
                );
            }
        } else if (!lastRegion.equalsIgnoreCase(currentRegion)) {
            if (boundaryAlert.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(
                        Component.literal(String.format("§d[RegionMap] §6Boundary Crossed! §7Entered §a%s §7(from §c%s§7)",
                                currentRegion, lastRegion))
                );
            }
            lastRegion = currentRegion;
        }
    }

    public static String calculateRegion(double x, double z) {
        int rx = (int) Math.floor((x + 150_000.0) / REGION_SIZE);
        int rz = (int) Math.floor((z + 150_000.0) / REGION_SIZE);
        int index = Math.abs((rx * 3 + rz) % REGION_NAMES.length);
        return REGION_NAMES[index];
    }

    public String getCurrentRegion() {
        return lastRegion != null ? lastRegion : "Unknown";
    }
}
