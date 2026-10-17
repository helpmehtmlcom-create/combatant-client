/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.text.DecimalFormat;
import java.util.function.UnaryOperator;

public class NumberValue<N extends Number> extends ConfigValue<N> {

    /** Inclusive numeric range. */
    public record Range(double min, double max) {
        public double clamp(double v) {
            return v < min ? min : Math.min(v, max);
        }
    }

    private final N min, max;
    /** Narrows the declared range while a preset is active; null means the declared range always applies. */
    private UnaryOperator<Range> activeBounds;

    public NumberValue(String name, N def, N min, N max) {
        super(name, def);
        this.min = min;
        this.max = max;
        clamp();
    }

    /**
     * Declares a range that narrows the hard range while it applies (for example under an anticheat preset). The
     * stored value is never rewritten: {@link #get()} returns it clamped into the active range and
     * {@link #toJson()} keeps saving it, so dropping the narrowing restores what the player picked.
     */
    public void bindActiveBounds(UnaryOperator<Range> bounds) {
        this.activeBounds = bounds;
    }

    /** Declared (config file) minimum, independent of any active narrowing. */
    public N getHardMin() {
        return min;
    }

    /** Declared (config file) maximum, independent of any active narrowing. */
    public N getHardMax() {
        return max;
    }

    /** Range the value is currently allowed to take; UI sliders and typed input use this. */
    public Range activeRange() {
        Range hard = new Range(min.doubleValue(), max.doubleValue());
        if (activeBounds == null) return hard;
        Range narrowed = activeBounds.apply(hard);
        return narrowed == null ? hard : narrowed;
    }

    public N getMin() {
        return castToType(activeRange().min());
    }

    public N getMax() {
        return castToType(activeRange().max());
    }

    /** True when the active range has collapsed to one value inside a range that normally has several. */
    public boolean isForced() {
        if (activeBounds == null) return false;
        Range active = activeRange();
        return active.min() == active.max() && min.doubleValue() != max.doubleValue();
    }

    /** The stored value, ignoring any active narrowing. This is what gets saved. */
    public N getRaw() {
        return value;
    }

    /** Effective value: the stored one clamped into {@link #activeRange()}. */
    @Override
    public N get() {
        if (activeBounds == null) return value;
        double v = value.doubleValue();
        double clamped = activeRange().clamp(v);
        return clamped == v ? value : castToType(clamped);
    }

    /** Stores {@code v} clamped into the active range; ignored while the active range is a single forced value. */
    @Override
    public void set(N v) {
        if (isForced()) return;
        value = v;
        if (activeBounds != null) {
            double d = v.doubleValue();
            double clamped = activeRange().clamp(d);
            if (clamped != d) value = castToType(clamped);
        }
    }

    private void clamp() {
        double v = value.doubleValue();
        double lo = min.doubleValue();
        double hi = max.doubleValue();

        if (v < lo) value = castToType(lo);
        else if (v > hi) value = castToType(hi);
    }

    @SuppressWarnings("unchecked")
    public N castToType(double v) {
        if (value instanceof Integer) return (N) Integer.valueOf((int) v);
        if (value instanceof Float) return (N) Float.valueOf((float) v);
        if (value instanceof Long) return (N) Long.valueOf((long) v);
        return (N) Double.valueOf(v);
    }

    @Override
    public Object toJson() {
        double v = value.doubleValue();
        if (value instanceof Float || value instanceof Double) {
            v = Math.round(v * 100d) / 100d;
        }
        return v;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof Number n) {
            value = castToType(n.doubleValue());
            clamp();
        }
    }

    @Override
    public String toDisplay() {
        N effective = get();
        if (effective instanceof Float || effective instanceof Double) {
            return new DecimalFormat("0.##").format(effective.doubleValue());
        }
        return String.valueOf(effective);
    }
}
