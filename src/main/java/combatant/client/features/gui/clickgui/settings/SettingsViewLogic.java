/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure layout/filter rules for the module settings page (unit-tested). */
public final class SettingsViewLogic {
    private SettingsViewLogic() {
    }

    /** A run of consecutive settings {@code [start, end)} sharing the leading name token {@code key}. */
    public record Section(String key, int start, int end) {
        public int size() {
            return end - start;
        }
    }

    /** First word of a setting id: splits on '_', '-', ' ' and camelCase humps; lower-cased. */
    public static String leadToken(String id) {
        if (id == null) return "";
        int n = id.length();
        for (int i = 0; i < n; i++) {
            char c = id.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                return id.substring(0, i).toLowerCase(Locale.ROOT);
            }
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(id.charAt(i - 1))) {
                return id.substring(0, i).toLowerCase(Locale.ROOT);
            }
        }
        return id.toLowerCase(Locale.ROOT);
    }

    /**
     * Finds runs of at least {@code minRun} consecutive ids with the same lead token. Ids made of a single
     * word (token equals the whole id) never start a section, so plain toggles are not grouped by accident.
     */
    public static List<Section> sections(List<String> ids, int minRun) {
        List<Section> out = new ArrayList<>();
        int i = 0;
        while (i < ids.size()) {
            String token = leadToken(ids.get(i));
            int j = i + 1;
            while (j < ids.size() && !token.isEmpty() && token.equals(leadToken(ids.get(j)))) j++;
            if (j - i >= minRun && !token.isEmpty()) {
                out.add(new Section(token, i, j));
                i = j;
            } else {
                i++;
            }
        }
        return out;
    }

    /** Case-insensitive substring filter over any of the given fields; blank query matches everything. */
    public static boolean matches(String query, String... fields) {
        if (query == null) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return true;
        for (String f : fields) {
            if (f != null && f.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    /** Capitalizes a section key for display ("aim" -> "Aim"). */
    public static String titleFor(String key) {
        if (key == null || key.isEmpty()) return "";
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
