/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

@ModuleInfo(
        id = "reloadchunks",
        displayName = "Reload Chunks",
        description = "Forces client-side chunk reload and cycles render distance to refresh entity and tile tracking.",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"rechunk", "f3a"}
)
public class ReloadChunks extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Integer> lowDistance =
            num("reload_chunks_low_dist", "low_distance", 5, 2, 32);

    @Override
    public void onEnable() {
        if (mc.levelExtractor != null) {
            mc.levelExtractor.allChanged();
        } else if (mc.levelRenderer != null) {
            mc.levelRenderer.resetLevelRenderData();
        }
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§a[ReloadChunks] §7Reloaded all chunks."));
        }
        setEnabled(false);
    }
}
