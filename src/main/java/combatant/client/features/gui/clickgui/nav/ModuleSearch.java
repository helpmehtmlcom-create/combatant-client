/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.nav;

import java.util.List;
import java.util.Locale;

/**
 * Pure, tiered module search ranking. Lower rank is a better match; {@link #NO_MATCH} means no hit.
 * Tiers (best first): name exact/prefix/word-prefix/substring, alias exact/prefix/substring,
 * name fuzzy (subsequence), setting name, description.
 */
public final class ModuleSearch {
    public static final int NO_MATCH = Integer.MAX_VALUE;
    /** Queries shorter than this only match names and aliases, never descriptions or settings. */
    public static final int MIN_DEEP_QUERY = 2;

    public enum Field { NONE, NAME, ALIAS, SETTING, DESCRIPTION }

    public record Result(int rank, Field field, String matchedText) {
        public static final Result NONE = new Result(NO_MATCH, Field.NONE, null);

        public boolean matched() {
            return rank != NO_MATCH;
        }
    }

    private ModuleSearch() {
    }

    public static Result match(String query, String name, List<String> aliases,
                               List<String> settingNames, String description) {
        String q = normalize(query);
        if (q.isEmpty()) return new Result(0, Field.NAME, name);

        int nameRank = textRank(q, name);
        if (nameRank != NO_MATCH) return new Result(nameRank, Field.NAME, name);

        if (aliases != null) {
            int best = NO_MATCH;
            String bestAlias = null;
            for (String alias : aliases) {
                int r = textRank(q, alias);
                if (r != NO_MATCH && (r < best || (r == best && bestAlias != null && alias.length() < bestAlias.length()))) {
                    best = r;
                    bestAlias = alias;
                }
            }
            if (bestAlias != null) return new Result(100 + best, Field.ALIAS, bestAlias);
        }

        int fuzzy = fuzzyRank(q, name);
        if (fuzzy != NO_MATCH) return new Result(200 + fuzzy, Field.NAME, name);

        if (compact(q).length() >= MIN_DEEP_QUERY) {
            if (settingNames != null) {
                int best = NO_MATCH;
                String bestSetting = null;
                for (String setting : settingNames) {
                    int r = textRank(q, setting);
                    if (r != NO_MATCH && r < best) {
                        best = r;
                        bestSetting = setting;
                    }
                }
                if (bestSetting != null) return new Result(300 + best, Field.SETTING, bestSetting);
            }
            if (description != null && containsAllTokens(q, description)) {
                return new Result(400, Field.DESCRIPTION, description);
            }
        }
        return Result.NONE;
    }

    /** Rank of a single candidate string: 0 exact, 1 prefix, 2 word prefix, 3 substring, else NO_MATCH. */
    public static int textRank(String normalizedQuery, String candidate) {
        if (candidate == null || candidate.isBlank()) return NO_MATCH;
        String c = candidate.toLowerCase(Locale.ROOT);
        String q = normalizedQuery;
        String cc = compact(c);
        String qc = compact(q);
        if (qc.isEmpty()) return NO_MATCH;
        if (c.equals(q) || cc.equals(qc)) return 0;
        if (c.startsWith(q) || cc.startsWith(qc)) return 1;
        for (String word : c.split("[\\s_\\-]+")) {
            if (!word.isEmpty() && word.startsWith(q)) return 2;
        }
        if (c.contains(q) || cc.contains(qc)) return 3;
        return NO_MATCH;
    }

    /** Subsequence match on the compacted name; tighter matches rank better (0..99). */
    public static int fuzzyRank(String normalizedQuery, String candidate) {
        if (candidate == null) return NO_MATCH;
        String qc = compact(normalizedQuery);
        String cc = compact(candidate.toLowerCase(Locale.ROOT));
        if (qc.length() < 3 || qc.length() > cc.length()) return NO_MATCH;
        int qi = 0;
        int first = -1;
        int last = -1;
        for (int i = 0; i < cc.length() && qi < qc.length(); i++) {
            if (cc.charAt(i) == qc.charAt(qi)) {
                if (first < 0) first = i;
                last = i;
                qi++;
            }
        }
        if (qi < qc.length()) return NO_MATCH;
        int span = last - first + 1;
        int gaps = span - qc.length();
        // Reject very sparse matches: more gaps than matched characters.
        if (gaps > qc.length()) return NO_MATCH;
        return Math.min(99, gaps * 3 + first);
    }

    private static boolean containsAllTokens(String q, String text) {
        String t = text.toLowerCase(Locale.ROOT);
        for (String token : q.split("\\s+")) {
            if (!token.isEmpty() && !t.contains(token)) return false;
        }
        return true;
    }

    public static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private static String compact(String value) {
        return value.replace(" ", "").replace("_", "").replace("-", "");
    }
}
