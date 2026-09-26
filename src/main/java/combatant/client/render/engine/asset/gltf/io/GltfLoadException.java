/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are adapted from GFBS-glTF 1.5.1
 * (commit 8906d4083c23b5d5bbf5191a0555d149a5272900).
 * Copyright (c) 2026 LytharaLab. Original portions are licensed under the MIT License.
 * See THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt.
 *
 * Combatant modifications are distributed under GNU GPL v3.0 as part of Combatant.
 */
package combatant.client.render.engine.asset.gltf.io;

import net.minecraft.resources.Identifier;

import java.io.IOException;

public final class GltfLoadException extends IOException {
    private static final long serialVersionUID = 1L;

    public GltfLoadException(Identifier asset, String message) {
        super("Failed to load " + asset + ": " + message);
    }

    public GltfLoadException(Identifier asset, String message, Throwable cause) {
        super("Failed to load " + asset + ": " + message, cause);
    }

    public GltfLoadException(String message) {
        super(message);
    }

    public GltfLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
