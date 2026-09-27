/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.StringValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.mixins.accessors.AbstractSignEditScreenAccessor;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.SignBlockEntity;

@ModuleInfo(
        id = "autosign",
        displayName = "AutoSign",
        aliases = {"SignWriter", "FastSign"},
        category = ModuleCategory.MISC,
        description = "Automatically fills out signs with preconfigured text when placed or edited"
)
public class AutoSign extends Module {

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue line1 = text("line1", "Line 1", "");
    private final StringValue line2 = text("line2", "Line 2", "");
    private final StringValue line3 = text("line3", "Line 3", "");
    private final StringValue line4 = text("line4", "Line 4", "");

    public AutoSign() {
        super();
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.player == null) return;

        Screen screen = ClientScreen.current(mc);
        if (screen instanceof AbstractSignEditScreen signScreen) {
            AbstractSignEditScreenAccessor accessor = (AbstractSignEditScreenAccessor) signScreen;
            SignBlockEntity sign = accessor.combatant$getSign();
            boolean isFront = accessor.combatant$isFrontText();

            if (sign != null && mc.getConnection() != null) {
                String l1 = line1.get() != null ? line1.get() : "";
                String l2 = line2.get() != null ? line2.get() : "";
                String l3 = line3.get() != null ? line3.get() : "";
                String l4 = line4.get() != null ? line4.get() : "";

                mc.getConnection().send(new ServerboundSignUpdatePacket(
                        sign.getBlockPos(),
                        isFront,
                        l1, l2, l3, l4
                ));

                ClientScreen.show(mc, null);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_WORK_CARTOGRAPHER, 1.0f));
                CommandOutput.success("AutoSign: text applied.");
            }
        }
    }
}
