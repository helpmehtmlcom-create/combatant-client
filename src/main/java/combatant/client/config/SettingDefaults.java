/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Pure helpers for the settings UI: default-vs-current diffing on serialized (JSON-shaped) values,
 * and number formatting/parsing for slider text entry.
 */
public final class SettingDefaults {
    private SettingDefaults() {
    }

    /** Deep equality over JSON-shaped values; numbers compare by value, so {@code 1} equals {@code 1.0}. */
    public static boolean jsonEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            if (ma.size() != mb.size()) return false;
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!mb.containsKey(e.getKey())) return false;
                if (!jsonEquals(e.getValue(), mb.get(e.getKey()))) return false;
            }
            return true;
        }
        if (a instanceof Collection<?> ca && b instanceof Collection<?> cb) {
            if (ca.size() != cb.size()) return false;
            Iterator<?> ia = ca.iterator();
            Iterator<?> ib = cb.iterator();
            while (ia.hasNext()) {
                if (!jsonEquals(ia.next(), ib.next())) return false;
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    /** True when {@code current} differs from {@code defaultValue}. A missing default never counts as changed. */
    public static boolean isChanged(Object defaultValue, Object current) {
        return defaultValue != null && !jsonEquals(defaultValue, current);
    }

    /** Formats a slider value: integers without a fraction, decimals trimmed to {@code maxDecimals}. */
    public static String formatNumber(double value, boolean integer, int maxDecimals) {
        if (!Double.isFinite(value)) return "0";
        if (integer) return Long.toString(Math.round(value));
        double scale = Math.pow(10, Math.max(0, maxDecimals));
        double rounded = Math.round(value * scale) / scale;
        if (rounded == Math.rint(rounded) && Math.abs(rounded) < 1e15) return Long.toString((long) rounded);
        String s = java.math.BigDecimal.valueOf(rounded).stripTrailingZeros().toPlainString();
        return s;
    }

    /**
     * Parses user-typed slider text (comma or dot decimal) and clamps to range.
     * Returns NaN when the text is not a finite number or is a fraction for an integer setting.
     */
    public static double parseNumber(String raw, boolean integer, double min, double max) {
        if (raw == null) return Double.NaN;
        String t = raw.trim().replace(',', '.');
        if (t.isEmpty() || t.equals("-") || t.equals(".") || t.equals("-.")) return Double.NaN;
        if (integer && t.indexOf('.') >= 0) return Double.NaN;
        double v;
        try {
            v = Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
        if (!Double.isFinite(v)) return Double.NaN;
        v = Math.max(min, Math.min(max, v));
        return integer ? Math.round(v) : v;
    }

    /** Next value after an arrow/scroll step, clamped; {@code steps} may be negative. */
    public static double step(double current, double stepSize, int steps, double min, double max) {
        double next = current + stepSize * steps;
        return Math.max(min, Math.min(max, next));
    }
}
