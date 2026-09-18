/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hides or randomizes armor trims on player models to boost rendering performance and reduce visual clutter.
 * Ported from 67Client's ArmorTrimHiderModule.
 */
@ModuleInfo(
        id = "armortrimhider",
        displayName = "ArmorTrimHider",
        category = ModuleCategory.VISUALS,
        subCategory = ModuleSubCategory.DONUTSMP,
        description = "Hides armor trims on players to boost FPS and reduce visual clutter in crowded PvP."
)
public class ArmorTrimHider extends Module {

    public enum Mode {
        HIDE,
        RANDOM
    }

    private final EnumValue<Mode> mode =
            enumSetting("armortrim_mode", "mode", Mode.HIDE, Mode.values());
    private final BooleanValue ownArmor =
            bool("armortrim_own_armor", "own_armor", false);

    private RegistryAccess cachedAccess;
    private final List<Holder<TrimMaterial>> materials = new ArrayList<>();
    private final List<Holder<TrimPattern>> patterns = new ArrayList<>();

    public boolean affectsOwnArmor() {
        return ownArmor.get();
    }

    @Nullable
    public ArmorTrim mapTrim(ItemStack stack, @Nullable ArmorTrim original, boolean isLocalPlayer) {
        if (!isEnabled()) {
            return original;
        }
        if (isLocalPlayer && !ownArmor.get()) {
            return original;
        }

        if (mode.get() == Mode.RANDOM) {
            ArmorTrim random = getRandomTrim(stack);
            return random != null ? random : original;
        } else {
            return null; // Hide trim completely
        }
    }

    @Nullable
    private ArmorTrim getRandomTrim(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        RegistryAccess access = mc.level.registryAccess();
        if (access != cachedAccess) {
            rebuildCache(access);
        }

        if (!materials.isEmpty() && !patterns.isEmpty()) {
            Random rng = new Random(stack.getItem().getDescriptionId().hashCode());
            Holder<TrimMaterial> material = materials.get(rng.nextInt(materials.size()));
            Holder<TrimPattern> pattern = patterns.get(rng.nextInt(patterns.size()));
            return new ArmorTrim(material, pattern);
        }
        return null;
    }

    private void rebuildCache(RegistryAccess access) {
        cachedAccess = access;
        materials.clear();
        patterns.clear();
        try {
            access.lookupOrThrow(Registries.TRIM_MATERIAL).listElements().forEach(materials::add);
            access.lookupOrThrow(Registries.TRIM_PATTERN).listElements().forEach(patterns::add);
        } catch (Exception ignored) {
            materials.clear();
            patterns.clear();
        }
    }

    @Override
    public void onDisable() {
        cachedAccess = null;
        materials.clear();
        patterns.clear();
    }
}
