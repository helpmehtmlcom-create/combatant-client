/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources;

import combatant.client.util.resources.asset.AssetAutoLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical access boundary for Combatant resource-pack assets.
 *
 * <p>Renderer/importer code should depend on this helper instead of duplicating
 * {@link ResourceManager} lookup/error handling. Enum-backed built-in assets are resolved through
 * {@link AssetAutoLoader}, while dynamic identifiers (for example external glTF buffers and
 * textures) still use the same resource-pack access path.</p>
 */
public enum ResourceAccess {
    ;

    public static Optional<Resource> find(ResourceManager manager, Identifier id) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(id, "id");
        return manager.getResource(id);
    }

    public static Resource require(ResourceManager manager, Identifier id) throws IOException {
        return find(manager, id).orElseThrow(() ->
                new FileNotFoundException("Missing Combatant resource: " + id));
    }

    public static InputStream open(ResourceManager manager, Identifier id) throws IOException {
        return require(manager, id).open();
    }

    public static Identifier id(Enum<?> asset) {
        return AssetAutoLoader.resourceAsset(Objects.requireNonNull(asset, "asset"));
    }

    public static Resource require(ResourceManager manager, Enum<?> asset) throws IOException {
        return require(manager, id(asset));
    }

    public static InputStream open(ResourceManager manager, Enum<?> asset) throws IOException {
        return open(manager, id(asset));
    }
}
