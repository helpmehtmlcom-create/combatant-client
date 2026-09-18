/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.network.chat.Component;

/**
 * Fakes DonutSMP rank tags like [SR.MOD], [MEDIA], or [SR.ADMIN] for recordings and content creation.
 * Ported and adapted from 67Client's FakeRolesModule.
 */
@ModuleInfo(
        id = "fakeroles",
        displayName = "FakeRoles",
        description = "Fakes [SR.MOD], [MEDIA], [SR.ADMIN], or custom rank tags for clips and recordings.",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"fakerank", "customrank", "roletag"}
)
public class FakeRoles extends Module {

    public enum Role {
        SR_MOD("SR.MOD", "§a", 0x55FF55),
        SR_ADMIN("SR.ADMIN", "§c", 0xFF5555),
        MEDIA("MEDIA", "§d", 0xFF55FF),
        OWNER("OWNER", "§4", 0xAA0000),
        DEV("DEV", "§b", 0x55FFFF),
        CUSTOM("CUSTOM", "§e", 0xFFFF55);

        private final String label;
        private final String colorCode;
        private final int colorRgb;

        Role(String label, String colorCode, int colorRgb) {
            this.label = label;
            this.colorCode = colorCode;
            this.colorRgb = colorRgb;
        }

        public String getLabel() {
            return label;
        }

        public String getColorCode() {
            return colorCode;
        }

        public int getColorRgb() {
            return colorRgb;
        }
    }

    private final EnumValue<Role> role =
            enumCommon("fakeroles_role", "role", Role.SR_MOD, Role.values());
    private final StringValue customTag =
            text("fakeroles_custom", "custom_tag", "SR.MOD");
    private final BooleanValue showInChat =
            bool("fakeroles_chat", "chat", true);
    private final BooleanValue showInTab =
            bool("fakeroles_tab", "tab_list", true);

    public boolean isChatActive() {
        return isEnabled() && showInChat.get();
    }

    public boolean isTabActive() {
        return isEnabled() && showInTab.get();
    }

    public String getFormattedTag() {
        Role current = role.get();
        String tagText = (current == Role.CUSTOM) ? customTag.get() : current.getLabel();
        return "§8[" + current.getColorCode() + tagText + "§8] " + current.getColorCode();
    }

    public Component getFormattedComponent() {
        return Component.literal(getFormattedTag());
    }
}
