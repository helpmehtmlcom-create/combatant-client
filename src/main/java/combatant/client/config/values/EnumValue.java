/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.*;
import java.util.function.Supplier;

public class EnumValue<E extends Enum<E>> extends ConfigValue<E> {

    /**
     * Options offered while a preset narrows the list. {@code options == null} means every declared option;
     * {@code fallback} is the effective value when the stored one is not offered.
     */
    public record Allowed<E>(Set<E> options, E fallback) {
        public boolean allows(E option) {
            return options == null || options.contains(option);
        }
    }

    private final List<E> options;
    private final List<String> optionIds;
    private final Map<String, E> lookup;
    private Supplier<Allowed<E>> allowed;
    public EnumValue(String name, E def, Class<E> enumClass) {
        this(name, def, enumClass.getEnumConstants());
    }
    @SafeVarargs
    public EnumValue(String name, E def, E... opts) {
        super(name, def);
        this.options = List.of(opts);
        this.optionIds = new ArrayList<>(options.size());
        this.lookup = new HashMap<>();

        for (E opt : options) {
            String id = idOf(opt);
            optionIds.add(id);
            lookup.put(normalize(id), opt);
        }

        for (E opt : options) {
            if (!(opt instanceof AliasProvider aliasProvider)) continue;
            List<String> aliases = aliasProvider.aliases();
            if (aliases == null) continue;
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) continue;
                lookup.putIfAbsent(normalize(alias), opt);
            }
        }

        if (!options.contains(def) && !options.isEmpty()) {
            this.value = options.get(0);
        }
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    /**
     * Declares the options a preset allows. The stored value is never rewritten: {@link #get()} returns it while
     * it is allowed and the rule's fallback otherwise, {@link #toJson()} keeps saving it, so dropping the
     * restriction brings the player's pick back.
     */
    public void bindAllowed(Supplier<Allowed<E>> allowed) {
        this.allowed = allowed;
    }

    private Allowed<E> activeRule() {
        if (allowed == null) return null;
        Allowed<E> rule = allowed.get();
        return rule == null || rule.options() == null ? null : rule;
    }

    /** Ids the UI may offer right now, in declared order. */
    public List<String> getOptions() {
        Allowed<E> rule = activeRule();
        if (rule == null) return optionIds;
        List<String> ids = new ArrayList<>(rule.options().size());
        for (E opt : options) {
            if (rule.options().contains(opt)) ids.add(idOf(opt));
        }
        return ids;
    }

    /** True when only one option is offered out of several declared ones. */
    public boolean isForced() {
        Allowed<E> rule = activeRule();
        return rule != null && options.size() > 1 && getOptions().size() <= 1;
    }

    public boolean isAllowed(E option) {
        Allowed<E> rule = activeRule();
        return rule == null || rule.allows(option);
    }

    /** The stored option, ignoring any restriction. This is what gets saved. */
    public E getRaw() {
        return value;
    }

    /** Effective option: the stored one if currently allowed, else the rule's fallback. */
    @Override
    public E get() {
        Allowed<E> rule = activeRule();
        if (rule == null || rule.allows(value)) return value;
        if (rule.fallback() != null) return rule.fallback();
        for (E opt : options) {
            if (rule.allows(opt)) return opt;
        }
        return value;
    }

    /** Stores {@code v} only if it is currently allowed. */
    @Override
    public void set(E v) {
        if (isAllowed(v)) value = v;
    }

    public String getId() {
        return idOf(get());
    }

    /** Selects an option by id; options the active preset does not offer are ignored. */
    public void setById(String id) {
        if (id == null) return;
        E resolved = lookup.get(normalize(id));
        if (resolved != null) set(resolved);
    }

    @Override
    public Object toJson() {
        return idOf(value);
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof String s) {
            E resolved = lookup.get(normalize(s));
            if (resolved != null) value = resolved;
        }
    }

    private String idOf(E value) {
        if (value instanceof IdProvider idProvider) {
            return idProvider.getId();
        }
        return EnumIds.defaultId(value);
    }

    public interface IdProvider {
        default String getId() {
            return id();
        }

        default String id() {
            try {
                var method = getClass().getMethod("getId");
                if (method.getReturnType() == String.class && method.getParameterCount() == 0) {
                    return (String) method.invoke(this);
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to the enum name below.
            }

            if (this instanceof Enum<?> enumValue) {
                return EnumIds.defaultId(enumValue);
            }

            return "";
        }
    }

    public interface AliasProvider {
        default List<String> aliases() {
            return List.of();
        }
    }
}
