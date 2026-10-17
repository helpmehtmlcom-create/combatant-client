/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui;

import combatant.client.features.gui.clickgui.nav.ModuleSearch;
import combatant.client.util.text.SingleLineTextInput;

import java.util.Locale;

public enum ClickGuiSearch {
    ;

    private static boolean active = false;
    private static final SingleLineTextInput INPUT = new SingleLineTextInput(64);

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean v) {
        active = v;
        if (!v) {
            INPUT.clear();
        }
    }

    /** Removes keyboard focus but keeps the current query/filter intact. */
    public static void unfocus() {
        active = false;
        INPUT.unfocus();
    }

    /** Clears both keyboard focus and the current query. */
    public static void deactivate() {
        active = false;
        INPUT.unfocus();
        INPUT.clear();
    }

    public static boolean hasQuery() {
        return !INPUT.text().isBlank();
    }

    public static String getText() {
        return INPUT.text();
    }

    public static SingleLineTextInput editor() { return INPUT; }

    public static void append(char c) { INPUT.type(c); }

    public static void backspace() { INPUT.backspace(); }

    public static boolean keyPressed(int keyCode, int modifiers) { return INPUT.keyPressed(keyCode, modifiers); }

    /** Simple name filter used by non-module tabs (themes, configs, relations). */
    public static boolean matches(String candidate) {
        if (INPUT.text().isBlank()) return true;
        return ModuleSearch.textRank(ModuleSearch.normalize(INPUT.text()), candidate) != ModuleSearch.NO_MATCH;
    }

    public static boolean matches(String candidate, Iterable<String> aliases) {
        if (matches(candidate)) return true;
        if (aliases == null) return false;
        for (String alias : aliases) {
            if (matches(alias)) return true;
        }
        return false;
    }

    /** Full tiered module match (name, alias, fuzzy name, setting names, description). */
    public static ModuleSearch.Result matchModule(String name, java.util.List<String> aliases,
                                                  java.util.List<String> settingNames, String description) {
        return ModuleSearch.match(INPUT.text(), name, aliases, settingNames, description);
    }
}
