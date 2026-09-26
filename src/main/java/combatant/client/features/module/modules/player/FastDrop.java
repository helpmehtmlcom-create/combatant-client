/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

@ModuleInfo(
        id = "fastdrop",
        displayName = "FastDrop",
        aliases = {"QuickDrop", "ItemDump"},
        category = ModuleCategory.PLAYER,
        description = "Rapidly drops items from your hand or inventory."
)
public final class FastDrop extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final BooleanValue dropAll =
            bool("fastDropAll", "drop_all", true);

    private final NumberValue<Integer> delayTicks =
            num("fastDropDelayTicks", "delay_ticks", 0, 0, 5);

    private final BooleanValue autoDump =
            bool("fastDropAutoDump", "auto_dump", false);

    private int cooldown = 0;

    @Override
    public void onTick() {
        if (!isEnabled()) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (mc.options.keyDrop.isDown() || autoDump.get()) {
            if (!player.getMainHandItem().isEmpty()) {
                player.drop(dropAll.get());
                cooldown = delayTicks.get();
            }
        }
    }
}
