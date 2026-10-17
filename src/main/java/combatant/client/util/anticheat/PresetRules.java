/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.anticheat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What a {@link AntiCheatPreset} does to one setting, declared next to the setting and bound onto the value so the
 * GUI, config apply paths and the module all see the same effective value. {@link AntiCheatPreset#CUSTOM} never
 * restricts anything; only GRIM and VANILLA rules are declared.
 */
public final class PresetRules {

    private PresetRules() {
    }

    /** Numeric restriction: optional new lower/upper limit; {@code fixed} pins both. */
    public record Num(Double min, Double max) {
        public static final Num FREE = new Num(null, null);

        /** Never above {@code max}. */
        public static Num max(double max) {
            return new Num(null, max);
        }

        /** Never below {@code min}. */
        public static Num min(double min) {
            return new Num(min, null);
        }

        /** Exactly {@code value}; the setting is shown locked. */
        public static Num fixed(double value) {
            return new Num(value, value);
        }

        NumberValue.Range apply(NumberValue.Range hard) {
            double hi = max == null ? hard.max() : Math.min(hard.max(), max);
            double lo = min == null ? hard.min() : Math.max(hard.min(), min);
            if (max != null && max < lo) lo = Math.min(lo, max); // limit below the declared minimum: pin to the limit
            if (min != null && min > hi) hi = Math.max(hi, min);
            if (lo > hi) lo = hi;
            return new NumberValue.Range(lo, hi);
        }
    }

    /** Option restriction: only {@code options} are offered; {@code fallback} stands in for a stored option that is not. */
    public record Opt<E extends Enum<E>>(Set<E> options, E fallback) {
        /** Every option stays available. */
        public static <E extends Enum<E>> Opt<E> any() {
            return new Opt<>(null, null);
        }

        /** Everything except {@code forbidden}; a forbidden stored option reads as {@code fallback}. */
        @SafeVarargs
        public static <E extends Enum<E>> Opt<E> except(E fallback, E... forbidden) {
            EnumSet<E> allowed = EnumSet.allOf(fallback.getDeclaringClass());
            for (E f : forbidden) allowed.remove(f);
            allowed.add(fallback);
            return new Opt<>(allowed, fallback);
        }

        /** Only {@code allowed}; anything else stored reads as {@code fallback}. */
        @SafeVarargs
        public static <E extends Enum<E>> Opt<E> only(E fallback, E... allowed) {
            EnumSet<E> set = EnumSet.of(fallback, allowed);
            return new Opt<>(set, fallback);
        }

        /** Exactly {@code value}; the setting is shown locked. */
        public static <E extends Enum<E>> Opt<E> fixed(E value) {
            return new Opt<>(EnumSet.of(value), value);
        }

        EnumValue.Allowed<E> toAllowed() {
            return options == null ? null : new EnumValue.Allowed<>(options, fallback);
        }
    }

    /** Boolean restriction: {@code forced == null} leaves the player's choice alone. */
    public record Flag(Boolean forced) {
        public static final Flag FREE = new Flag(null);
        public static final Flag ON = new Flag(true);
        public static final Flag OFF = new Flag(false);
    }

    public static <N extends Number> void bind(NumberValue<N> value, Supplier<AntiCheatPreset> preset, Num grim, Num vanilla) {
        value.bindActiveBounds(hard -> {
            Num rule = switch (preset.get()) {
                case GRIM -> grim;
                case VANILLA -> vanilla;
                case CUSTOM -> null;
            };
            return rule == null ? hard : rule.apply(hard);
        });
    }

    public static <E extends Enum<E>> void bind(EnumValue<E> value, Supplier<AntiCheatPreset> preset, Opt<E> grim, Opt<E> vanilla) {
        value.bindAllowed(() -> {
            Opt<E> rule = switch (preset.get()) {
                case GRIM -> grim;
                case VANILLA -> vanilla;
                case CUSTOM -> null;
            };
            return rule == null ? null : rule.toAllowed();
        });
    }

    public static void bind(BooleanValue value, Supplier<AntiCheatPreset> preset, Flag grim, Flag vanilla) {
        value.bindForced(() -> switch (preset.get()) {
            case GRIM -> grim.forced();
            case VANILLA -> vanilla.forced();
            case CUSTOM -> null;
        });
    }
}
