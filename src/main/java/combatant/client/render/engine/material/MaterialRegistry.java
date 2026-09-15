/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.material;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resource-pack-backed terrain material registry.
 *
 * <p>IDs are derived from the exact sprite identifier used by Sodium. The registry never guesses a
 * material from rendered color/depth. Auxiliary maps are discovered by declared filename semantics;
 * absence remains explicit and uses scalar neutral fallbacks.</p>
 */
public final class MaterialRegistry {
    private static final MaterialRegistry GLOBAL = new MaterialRegistry();
    private static final int DEFAULT_ID = 0;

    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public static MaterialRegistry global() {
        return GLOBAL;
    }

    public void reload(ResourceManager resources) {
        if (resources == null) return;
        Set<Identifier> available = new HashSet<>();
        for (String namespace : resources.getNamespaces()) {
            available.addAll(resources.listResources("textures", id -> id.getPath().endsWith(".png")).keySet());
        }
        snapshot = new Snapshot(Set.copyOf(available), new HashMap<>());
    }

    public MaterialSurfaceDescriptor resolve(TextureAtlasSprite sprite, MaterialDomain domain) {
        if (sprite == null || sprite.contents() == null || sprite.contents().name() == null) {
            return fallback(domain);
        }
        Identifier spriteId = sprite.contents().name();
        Snapshot state = snapshot;
        CacheKey key = new CacheKey(spriteId, domain == null ? MaterialDomain.UNKNOWN : domain);
        synchronized (state.cache) {
            MaterialSurfaceDescriptor cached = state.cache.get(key);
            if (cached != null) return cached;
            MaterialSurfaceDescriptor resolved = build(state.available, spriteId, key.domain);
            state.cache.put(key, resolved);
            return resolved;
        }
    }

    private static MaterialSurfaceDescriptor build(Set<Identifier> available, Identifier spriteId, MaterialDomain domain) {
        EnumMap<MaterialTextureSemantic, Identifier> maps = new EnumMap<>(MaterialTextureSemantic.class);
        putIfPresent(available, maps, MaterialTextureSemantic.ALBEDO, texture(spriteId, ""));
        putIfPresent(available, maps, MaterialTextureSemantic.NORMAL, texture(spriteId, "_normal"));
        putIfPresent(available, maps, MaterialTextureSemantic.AMBIENT_OCCLUSION, texture(spriteId, "_ao"));
        putIfPresent(available, maps, MaterialTextureSemantic.ROUGHNESS, texture(spriteId, "_roughness"));
        putIfPresent(available, maps, MaterialTextureSemantic.METALLIC, texture(spriteId, "_metallic"));
        putIfPresent(available, maps, MaterialTextureSemantic.SPECULAR, texture(spriteId, "_specular"));
        putIfPresent(available, maps, MaterialTextureSemantic.EMISSIVE, texture(spriteId, "_emissive"));
        putIfPresent(available, maps, MaterialTextureSemantic.HEIGHT, texture(spriteId, "_height"));
        putIfPresent(available, maps, MaterialTextureSemantic.ORM, texture(spriteId, "_orm"));
        putIfPresent(available, maps, MaterialTextureSemantic.LABPBR_NORMAL, texture(spriteId, "_n"));
        putIfPresent(available, maps, MaterialTextureSemantic.LABPBR_SPECULAR, texture(spriteId, "_s"));

        int stableId = stableId32(spriteId);
        if (stableId == DEFAULT_ID) stableId = 1;
        return new MaterialSurfaceDescriptor(
                stableId, spriteId, domain, new MaterialTextureSet(maps),
                1.0f, 0.72f, 0.0f, 0.04f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 0.5f, 1.0f
        );
    }

    private static MaterialSurfaceDescriptor fallback(MaterialDomain domain) {
        return new MaterialSurfaceDescriptor(
                DEFAULT_ID, Identifier.fromNamespaceAndPath("combatant", "unknown"),
                domain == null ? MaterialDomain.UNKNOWN : domain,
                new MaterialTextureSet(null),
                1.0f, 0.72f, 0.0f, 0.04f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 0.5f, 1.0f
        );
    }

    private static void putIfPresent(Set<Identifier> available,
                                     Map<MaterialTextureSemantic, Identifier> target,
                                     MaterialTextureSemantic semantic,
                                     Identifier id) {
        if (available.contains(id)) target.put(semantic, id);
    }

    private static Identifier texture(Identifier sprite, String suffix) {
        return Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + suffix + ".png");
    }

    /** Stable 32-bit FNV-1a producer ID derived only from the exact sprite identifier. */
    private static int stableId32(Identifier id) {
        byte[] bytes = id.toString().getBytes(StandardCharsets.UTF_8);
        int hash = 0x811C9DC5;
        for (byte b : bytes) {
            hash ^= b & 0xFF;
            hash *= 0x01000193;
        }
        return hash;
    }

    private record CacheKey(Identifier spriteId, MaterialDomain domain) { }

    private record Snapshot(Set<Identifier> available, Map<CacheKey, MaterialSurfaceDescriptor> cache) {
        private static final Snapshot EMPTY = new Snapshot(Set.of(), new HashMap<>());
    }
}
