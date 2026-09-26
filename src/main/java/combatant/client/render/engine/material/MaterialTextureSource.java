/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.material;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Backend-neutral source of a material texture semantic.
 * Resource-pack textures and textures embedded in imported assets share the same material contract.
 */
public sealed interface MaterialTextureSource permits MaterialTextureSource.Resource,
        MaterialTextureSource.Imported {

    record Resource(Identifier id) implements MaterialTextureSource {
        public Resource {
            Objects.requireNonNull(id, "id");
        }
    }

    record Imported(Identifier assetId, int textureIndex) implements MaterialTextureSource {
        public Imported {
            Objects.requireNonNull(assetId, "assetId");
            if (textureIndex < 0) throw new IllegalArgumentException("textureIndex");
        }
    }
}
