/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.StringValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Anonymizes your username and other players in chat, scoreboard, and tab list.
 * Ideal for streamers, YouTubers, and DonutSMP players preventing targeters.
 */
@ModuleInfo(
        id = "nameprotect",
        displayName = "NameProtect",
        category = ModuleCategory.MISC,
        subCategory = ModuleSubCategory.NORMAL,
        description = "Anonymizes your username and other players in chat, scoreboard, and tab list for streaming."
)
public class NameProtect extends Module {

    private final StringValue alias =
            text("nameprotect_alias", "your_alias", "You");
    private final BooleanValue selfOnly =
            bool("nameprotect_self_only", "self_only", true);
    private final StringValue otherAlias =
            text("nameprotect_other_alias", "others_alias", "Player");
    private final BooleanValue maskChat =
            bool("nameprotect_mask_chat", "mask_chat", true);
    private final BooleanValue maskTab =
            bool("nameprotect_mask_tab", "mask_tab", true);
    private final BooleanValue maskScoreboard =
            bool("nameprotect_mask_scoreboard", "mask_scoreboard", true);

    private Pattern selfPattern = null;
    private String lastSelfName = null;

    public boolean shouldMaskChat() {
        return isEnabled() && maskChat.get();
    }

    public boolean shouldMaskTab() {
        return isEnabled() && maskTab.get();
    }

    public boolean shouldMaskScoreboard() {
        return isEnabled() && maskScoreboard.get();
    }

    public String filter(String input) {
        if (!isEnabled() || input == null || input.isEmpty()) {
            return input;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            String selfName = mc.player.getName().getString();
            if (selfName != null && !selfName.isEmpty()) {
                if (!selfName.equals(lastSelfName) || selfPattern == null) {
                    lastSelfName = selfName;
                    selfPattern = Pattern.compile("(?i)\\b" + Pattern.quote(selfName) + "\\b");
                }
                String replacement = alias.get();
                if (replacement.isBlank()) replacement = "You";
                input = selfPattern.matcher(input).replaceAll(Matcher.quoteReplacement(replacement));
            }
        }

        return input;
    }

    public Component filter(Component component) {
        if (!isEnabled() || component == null) {
            return component;
        }
        String filtered = filter(component.getString());
        return Component.literal(filtered).withStyle(component.getStyle());
    }
}
