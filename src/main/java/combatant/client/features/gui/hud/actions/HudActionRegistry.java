/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.hud.actions;

import combatant.client.features.gui.clickgui.settings.FunctionBindSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HudActionRegistry {
    private static final Map<Class<?>, List<AnnotatedAction>> CACHE = new ConcurrentHashMap<>();

    private HudActionRegistry() {}

    /** Inventory-backed eligibility for item actions; offhand is not part of the 0..35 loop. */
    public static boolean hasItem(LocalPlayer player, Item item) {
        if (player == null || item == null) return false;
        if (player.getOffhandItem().is(item)) return true;
        for (int slot = 0; slot < 36; slot++) {
            if (player.getInventory().getItem(slot).is(item)) return true;
        }
        return false;
    }

    public record State(ItemStack stack, boolean available, boolean active, boolean aware, float cooldown,
                        boolean inProgress, float progress) {
        public State {
            stack = stack == null ? ItemStack.EMPTY : stack.copy();
            cooldown = Math.max(0.0f, Math.min(1.0f, cooldown));
            progress = Math.max(0.0f, Math.min(1.0f, progress));
        }

        public State(ItemStack stack, boolean available, boolean active, boolean aware, float cooldown) {
            this(stack, available, active, aware, cooldown, false, 0.0f);
        }
    }

    public record Snapshot(String id, String label, String description, String bind, String icon,
                           ItemStack stack, boolean moduleEnabled, boolean available,
                           boolean active, boolean aware, float cooldown, boolean inProgress, float progress) {
        public Snapshot(String id, String label, String description, String bind, String icon,
                        ItemStack stack, boolean moduleEnabled, boolean available,
                        boolean active, boolean aware, float cooldown) {
            this(id, label, description, bind, icon, stack, moduleEnabled,
                    available, active, aware, cooldown, false, 0.0f);
        }
    }

    private record AnnotatedAction(HudAction annotation, Method method) {}

    /** Itemizer actions must be explicitly annotated and backed by a real function bind. */
    public static List<String> actionIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Module module : ModuleManager.getModules()) {
            Map<String, AnnotatedAction> registered = describedActions(module);
            for (FunctionBindSetting bind : module.getActionSettings()) {
                if (registered.containsKey(bind.getActionId())) {
                    ids.add(module.name() + ":" + bind.getActionId());
                }
            }
        }
        return List.copyOf(ids);
    }

    public static List<Snapshot> snapshots() {
        ArrayList<Snapshot> results = new ArrayList<>();
        for (Module module : ModuleManager.getModules()) {
            Map<String, AnnotatedAction> registered = describedActions(module);
            for (FunctionBindSetting bind : module.getActionSettings()) {
                AnnotatedAction action = registered.get(bind.getActionId());
                if (action == null) continue;
                State state = state(module, action);
                if (state == null) continue;
                HudAction info = action.annotation();
                results.add(new Snapshot(module.name() + ":" + info.id(), info.label(),
                        info.description(), bind.get(), info.icon(), state.stack(), module.isEnabled(),
                        state.available(), state.active(), state.aware(), state.cooldown(),
                        state.inProgress(), state.progress()));
            }
        }
        results.sort(Comparator.comparing(Snapshot::id));
        return List.copyOf(results);
    }

    private static Map<String, AnnotatedAction> describedActions(Module module) {
        Map<String, AnnotatedAction> registered = new LinkedHashMap<>();
        for (AnnotatedAction action : CACHE.computeIfAbsent(module.getClass(), HudActionRegistry::discover)) {
            registered.put(action.annotation().id(), action);
        }
        return registered;
    }

    private static List<AnnotatedAction> discover(Class<?> type) {
        ArrayList<AnnotatedAction> result = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            HudAction meta = method.getAnnotation(HudAction.class);
            if (meta == null || method.getParameterCount() != 0 || method.getReturnType() != State.class) continue;
            method.setAccessible(true);
            result.add(new AnnotatedAction(meta, method));
        }
        result.sort(Comparator.comparing(item -> item.annotation().id()));
        return List.copyOf(result);
    }

    private static State state(Module module, AnnotatedAction action) {
        if (action == null) return null;
        try {
            return (State) action.method().invoke(module);
        } catch (ReflectiveOperationException | RuntimeException error) {
            return null;
        }
    }
}
