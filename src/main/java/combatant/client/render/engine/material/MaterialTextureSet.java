/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

import net.minecraft.resources.Identifier;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Immutable map from canonical material semantics to actual resource-pack textures. */
public final class MaterialTextureSet {
    private final Map<MaterialTextureSemantic, Identifier> textures;
    private final int presenceMask;

    public MaterialTextureSet(Map<MaterialTextureSemantic, Identifier> textures) {
        EnumMap<MaterialTextureSemantic, Identifier> copy = new EnumMap<>(MaterialTextureSemantic.class);
        if (textures != null) copy.putAll(textures);
        this.textures = Collections.unmodifiableMap(copy);
        int mask = 0;
        for (MaterialTextureSemantic semantic : copy.keySet()) {
            mask |= 1 << semantic.ordinal();
        }
        this.presenceMask = mask;
    }

    public Identifier texture(MaterialTextureSemantic semantic) {
        return textures.get(semantic);
    }

    public boolean has(MaterialTextureSemantic semantic) {
        return textures.containsKey(semantic);
    }

    public int presenceMask() {
        return presenceMask;
    }

    public Map<MaterialTextureSemantic, Identifier> asMap() {
        return textures;
    }
}
