/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class ModeValue extends ConfigValue<String> {

    private final List<String> options;

    public ModeValue(String name, String def, String... opts) {
        super(name, def);
        this.options = new ArrayList<>(Arrays.asList(opts));

        // если default не валидный → ставим первый вариант
        if (!options.contains(def)) {
            this.value = options.get(0);
        }
    }

    /** Returns the option to use while one is forced (for example by an anticheat preset), or null while free. */
    private Supplier<String> forced;

    /**
     * Declares a forced option. While it returns non-null, {@link #get()} returns it and it is the only offered
     * option; the stored option is left alone, still saved, and returns once the force lifts.
     */
    public void bindForced(Supplier<String> forced) {
        this.forced = forced;
    }

    private String activeForced() {
        if (forced == null) return null;
        String f = forced.get();
        return f != null && options.contains(f) ? f : null;
    }

    public boolean isForced() {
        return activeForced() != null;
    }

    /** The stored option, ignoring any force. This is what gets saved. */
    public String getRaw() {
        return value;
    }

    @Override
    public String get() {
        String f = activeForced();
        return f != null ? f : value;
    }

    @Override
    public void set(String v) {
        if (!isForced()) value = v;
    }

    public List<String> getOptions() {
        String f = activeForced();
        return f != null ? List.of(f) : List.copyOf(options);
    }

    public boolean addOption(String option) {
        String normalized = normalizeOption(option);
        if (normalized.isEmpty() || options.contains(normalized)) return false;
        options.add(normalized);
        return true;
    }

    public int addOptions(Collection<String> values) {
        if (values == null || values.isEmpty()) return 0;
        int added = 0;
        for (String value : values) {
            if (addOption(value)) added++;
        }
        return added;
    }

    public boolean removeOption(String option) {
        String normalized = normalizeOption(option);
        if (normalized.isEmpty()) return false;
        if (Objects.equals(value, normalized)) return false;
        return options.remove(normalized);
    }

    public void next() {
        int i = options.indexOf(value);
        value = options.get((i + 1) % options.size());
    }

    @Override
    public Object toJson() {
        return value;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof String s && options.contains(s)) {
            value = s;
        }
    }

    private static String normalizeOption(String option) {
        return option == null ? "" : option.trim();
    }
}
