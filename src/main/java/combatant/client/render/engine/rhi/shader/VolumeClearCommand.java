/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Clear one or more complete mip levels selected independently of shader view semantics. */
public record VolumeClearCommand(String label,
                                 RhiVolumeSubresource target,
                                 VolumeClearValue value) {
    public VolumeClearCommand {
        label = label == null || label.isBlank() ? "combatant-volume-clear" : label;
        if (target == null || value == null) throw new IllegalArgumentException("target/value");
        target.requireValid();
        if (!target.volume().descriptor().copyDestination()) {
            throw new IllegalArgumentException("Volume clear requires COPY_DST usage: "
                    + target.volume().descriptor().label());
        }
    }

    public VolumeClearCommand(String label, RhiVolumeView target, VolumeClearValue value) {
        this(label, subresource(target), value);
    }

    private static RhiVolumeSubresource subresource(RhiVolumeView target) {
        if (target == null) throw new IllegalArgumentException("target");
        target.requireValid();
        return new RhiVolumeSubresource(target.volume(),
                target.descriptor().baseMipLevel(), target.descriptor().mipLevels());
    }
}
