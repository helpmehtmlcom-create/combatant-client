/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.function.Supplier;

public class BooleanValue extends ConfigValue<Boolean> {

    /** Returns the value to use while one is forced (for example by an anticheat preset), or null while free. */
    private Supplier<Boolean> forced;

    public BooleanValue(String name, boolean def) {
        super(name, def);
    }

    /**
     * Declares a forced value. While it returns non-null, {@link #get()} returns it instead of the stored value,
     * the stored value is left alone (still saved, returns once the force lifts) and writes are ignored.
     */
    public void bindForced(Supplier<Boolean> forced) {
        this.forced = forced;
    }

    public boolean isForced() {
        return forced != null && forced.get() != null;
    }

    /** The stored value, ignoring any force. This is what gets saved. */
    public boolean getRaw() {
        return value;
    }

    @Override
    public Boolean get() {
        if (forced != null) {
            Boolean f = forced.get();
            if (f != null) return f;
        }
        return value;
    }

    @Override
    public void set(Boolean v) {
        if (isForced()) return;
        value = v;
    }

    @Override
    public Object toJson() {
        return value;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof Boolean b)
            value = b;
    }
}
