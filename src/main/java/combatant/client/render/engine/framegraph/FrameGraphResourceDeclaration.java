/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.framegraph;

import java.util.Objects;

/** Separates stable logical identity from concrete physical requirements. */
public record FrameGraphResourceDeclaration(FrameGraphResourceKey resource,
                                            FrameGraphPhysicalResourceDescriptor descriptor) {
    public FrameGraphResourceDeclaration {
        Objects.requireNonNull(resource, "resource");
        if (resource.lifetime() == FrameGraphResourceLifetime.EXTERNAL) {
            if (descriptor != null) throw new IllegalArgumentException("External resources cannot own allocations: " + resource.name());
        } else {
            Objects.requireNonNull(descriptor, "descriptor");
            if (descriptor.kind() != resource.kind()) {
                throw new IllegalArgumentException("Resource kind/descriptor mismatch for " + resource.name()
                        + ": " + resource.kind() + " vs " + descriptor.kind());
            }
        }
    }

    public static FrameGraphResourceDeclaration external(FrameGraphResourceKey resource) {
        return new FrameGraphResourceDeclaration(resource, null);
    }
}
