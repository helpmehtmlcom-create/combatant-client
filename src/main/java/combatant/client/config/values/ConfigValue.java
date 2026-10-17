/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

/**
 * Базовый тип для всех конфиг-значений.
 */
public abstract class ConfigValue<T> {

    private final String name; // <---- ЭТО ТЕБЕ НУЖНО
    protected T value;

    public ConfigValue(String name, T def) {
        this.name = name;
        this.value = def;
    }

    /**
     * ключ в конфиге
     */
    public String getName() {
        return name;
    }

    /**
     * Текущее значение
     */
    public T get() {
        return value;
    }

    /**
     * Установить новое значение
     */
    public void set(T v) {
        this.value = v;
    }

    /**
     * Сериализация:
     * Возвращает примитив (Boolean, Number, String) или структуру (Map/List).
     */
    public abstract Object toJson();

    /**
     * Десериализация:
     * На вход прилетает то же самое, что было возвращено в toJson().
     */
    public abstract void fromJson(Object json);

    /**
     * Для UI
     */
    public String toDisplay() {
        return String.valueOf(value);
    }

    private Object defaultJson;
    private boolean defaultCaptured;

    /** Snapshots the current value as the default. Idempotent: only the first call records. */
    public final void captureDefault() {
        if (defaultCaptured) return;
        defaultJson = toJson();
        defaultCaptured = true;
    }

    /** True when a default was captured and the current value differs from it. */
    public final boolean isChangedFromDefault() {
        return defaultCaptured && combatant.client.config.SettingDefaults.isChanged(defaultJson, toJson());
    }

    /** Restores the captured default; returns false when none was captured. */
    public final boolean resetToDefault() {
        if (!defaultCaptured) return false;
        fromJson(defaultJson);
        return true;
    }
}
