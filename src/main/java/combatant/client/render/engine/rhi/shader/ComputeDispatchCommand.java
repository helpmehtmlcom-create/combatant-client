/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.shader;

import java.util.List;

public record ComputeDispatchCommand(String label,
                                     RhiComputePipeline pipeline,
                                     int groupsX,
                                     int groupsY,
                                     int groupsZ,
                                     List<StorageBinding> storageBindings,
                                     List<SampledTextureBinding> sampledTextures,
                                     List<StorageImageBinding> storageImages,
                                     List<StorageVolumeBinding> storageVolumes,
                                     List<SampledVolumeBinding> sampledVolumes) {
    public ComputeDispatchCommand {
        label = label == null || label.isBlank() ? "combatant-compute" : label;
        if (pipeline == null) throw new IllegalArgumentException("pipeline");
        groupsX = Math.max(1, groupsX);
        groupsY = Math.max(1, groupsY);
        groupsZ = Math.max(1, groupsZ);
        storageBindings = storageBindings == null || storageBindings.isEmpty()
                ? List.of()
                : List.copyOf(storageBindings);
        sampledTextures = sampledTextures == null || sampledTextures.isEmpty()
                ? List.of()
                : List.copyOf(sampledTextures);
        storageImages = storageImages == null || storageImages.isEmpty()
                ? List.of()
                : List.copyOf(storageImages);
        storageVolumes = storageVolumes == null || storageVolumes.isEmpty()
                ? List.of()
                : List.copyOf(storageVolumes);
        sampledVolumes = sampledVolumes == null || sampledVolumes.isEmpty()
                ? List.of()
                : List.copyOf(sampledVolumes);
    }

    public ComputeDispatchCommand(String label,
                                  RhiComputePipeline pipeline,
                                  int groupsX,
                                  int groupsY,
                                  int groupsZ,
                                  List<StorageBinding> storageBindings,
                                  List<SampledTextureBinding> sampledTextures,
                                  List<StorageImageBinding> storageImages,
                                  List<StorageVolumeBinding> storageVolumes) {
        this(label, pipeline, groupsX, groupsY, groupsZ, storageBindings, sampledTextures,
                storageImages, storageVolumes, List.of());
    }

    public ComputeDispatchCommand(String label,
                                  RhiComputePipeline pipeline,
                                  int groupsX,
                                  int groupsY,
                                  int groupsZ,
                                  List<StorageBinding> storageBindings,
                                  List<SampledTextureBinding> sampledTextures,
                                  List<StorageImageBinding> storageImages) {
        this(label, pipeline, groupsX, groupsY, groupsZ, storageBindings, sampledTextures,
                storageImages, List.of(), List.of());
    }
}
