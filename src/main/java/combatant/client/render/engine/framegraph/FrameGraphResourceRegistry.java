/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import java.util.LinkedHashMap;
import java.util.Map;

/** Mutable front-end registry; compiled plans always consume immutable snapshots. */
public final class FrameGraphResourceRegistry {
    private final LinkedHashMap<FrameGraphResourceKey, FrameGraphResourceDeclaration> declarations = new LinkedHashMap<>();

    public synchronized void declare(FrameGraphResourceKey resource, FrameGraphPhysicalResourceDescriptor descriptor) {
        FrameGraphResourceDeclaration declaration = new FrameGraphResourceDeclaration(resource, descriptor);
        declarations.put(resource, declaration);
    }

    public synchronized void declareExternal(FrameGraphResourceKey resource) {
        if (resource == null || resource.lifetime() != FrameGraphResourceLifetime.EXTERNAL) {
            throw new IllegalArgumentException("External declaration requires EXTERNAL lifetime");
        }
        declarations.put(resource, FrameGraphResourceDeclaration.external(resource));
    }

    public synchronized void remove(FrameGraphResourceKey resource) {
        declarations.remove(resource);
    }

    public synchronized Map<FrameGraphResourceKey, FrameGraphResourceDeclaration> snapshot() {
        return Map.copyOf(declarations);
    }

    public synchronized boolean isEmpty() {
        return declarations.isEmpty();
    }

    public synchronized void clear() {
        declarations.clear();
    }
}
