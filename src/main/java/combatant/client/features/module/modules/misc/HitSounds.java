/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.DisableSettingI18n;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.AttackEntityEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.sound.SoundSystem;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

@ModuleInfo(
        id = "hitsounds",
        displayName = "HitSounds",
        category = ModuleCategory.MISC,
        description = "Plays customizable sound effects and hitsound alerts when landing attacks on opponents."
)
public class HitSounds extends Module {

    private static final String SETTING_SOUND = "sound";
    private static final String SETTING_VOLUME = "volume";

    @DisableSettingI18n(name = false, options = true)
    private final EnumValue<SoundChoice> sound = enumSetting(
            "hitsounds_sound",
            SETTING_SOUND,
            SoundChoice.BELL,
            SoundChoice.values()
    );

    private final NumberValue<Float> volume = num(
            "hitsounds_volume",
            SETTING_VOLUME,
            1.0f,
            0.0f,
            1.0f
    );

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!isEnabled() || event == null || event.getTarget() == null) return;
        handleHit(event.getTarget());
    }

    public void handleHit(Entity target) {
        if (!isEnabled() || target == null) return;
        SoundSystem.playCombatEffect(sound.get().name(), volume.get(), 1.0f);
    }

    public EnumValue<SoundChoice> getSound() {
        return sound;
    }

    public NumberValue<Float> getVolume() {
        return volume;
    }

    public enum SoundChoice implements EnumValue.IdProvider {
        BELL("Bell"),
        DING("Ding"),
        COD("Cod"),
        RUST("Rust"),
        METALLIC("Metallic");

        private final String displayName;

        SoundChoice(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
