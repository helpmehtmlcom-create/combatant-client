/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import combatant.client.render.sodium.SodiumSectionStorageView;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.QueuedSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

/** Sodium 0.9.1+mc26.2 adapter. Keep private storage details out of deferred renderer code. */
@Pseudo
@Mixin(value = QueuedSectionStorage.class, remap = false)
public abstract class SodiumQueuedSectionStorageMixin implements SodiumSectionStorageView {
    @Shadow @Final
    private Long2ReferenceMap<RenderSection> sections;

    @Override
    public Iterable<RenderSection> combatant$sections() {
        return sections.values();
    }
}
