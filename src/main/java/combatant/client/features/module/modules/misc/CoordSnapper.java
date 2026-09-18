/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.util.text.ClipboardUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Snaps looked-at block or player coordinates directly to the clipboard on DonutSMP.
 * Ported and adapted from 67Client's CoordSnapperModule.
 */
@ModuleInfo(
        id = "coordsnapper",
        displayName = "CoordSnapper",
        description = "Copies looked-at coordinates or current location to the clipboard with one key.",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"coordsnap", "copypos", "copycoords"}
)
public class CoordSnapper extends Module {

    public enum Format {
        SPACE("X Y Z"),
        COMMA("X, Y, Z"),
        BRACKETS("[X, Y, Z]"),
        TPA("/tpa X Y Z");

        private final String label;

        Format(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Target {
        LOOKED_AT("Looked At Block"),
        PLAYER_POS("Player Position");

        private final String label;

        Target(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final EnumValue<Format> format =
            enumCommon("coordsnapper_format", "format", Format.SPACE, Format.values());
    private final EnumValue<Target> target =
            enumCommon("coordsnapper_target", "target", Target.LOOKED_AT, Target.values());
    private final BooleanValue feedback =
            bool("coordsnapper_feedback", "feedback", true);

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onEnable() {
        snapCoordinates();
        // Automatically toggle off after snapping once per trigger
        setEnabled(false);
    }

    public void snapCoordinates() {
        if (mc.player == null) return;

        BlockPos pos = null;
        if (target.get() == Target.LOOKED_AT && mc.hitResult instanceof BlockHitResult blockHit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            pos = blockHit.getBlockPos();
        } else {
            pos = mc.player.blockPosition();
        }

        if (pos == null) return;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        String formatted = switch (format.get()) {
            case SPACE -> x + " " + y + " " + z;
            case COMMA -> x + ", " + y + ", " + z;
            case BRACKETS -> "[" + x + ", " + y + ", " + z + "]";
            case TPA -> "/tpa " + x + " " + y + " " + z;
        };

        ClipboardUtil.copy(formatted);

        if (feedback.get() && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("§a[CoordSnapper] §7Copied §f" + formatted + " §7to clipboard!")
            );
        }
    }
}
