/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.shader;

/** Exact-format 3D image copy between single-mip transfer subresources. */
public record VolumeCopyCommand(String label,
                                RhiVolumeSubresource source,
                                RhiVolumeSubresource destination,
                                int sourceX,
                                int sourceY,
                                int sourceZ,
                                int destinationX,
                                int destinationY,
                                int destinationZ,
                                int width,
                                int height,
                                int depth) {
    public VolumeCopyCommand {
        label = label == null || label.isBlank() ? "combatant-volume-copy" : label;
        if (source == null || destination == null) throw new IllegalArgumentException("source/destination");
        source.requireValid();
        destination.requireValid();
        if (source.mipLevels() != 1 || destination.mipLevels() != 1) {
            throw new IllegalArgumentException("Volume copy requires single-mip source and destination subresources");
        }
        if (!source.volume().descriptor().copySource()) {
            throw new IllegalArgumentException("Volume copy source lacks COPY_SRC usage: " + source.volume().descriptor().label());
        }
        if (!destination.volume().descriptor().copyDestination()) {
            throw new IllegalArgumentException("Volume copy destination lacks COPY_DST usage: " + destination.volume().descriptor().label());
        }
        if (source.volume().descriptor().format() != destination.volume().descriptor().format()) {
            throw new IllegalArgumentException("Volume copy requires identical formats: source="
                    + source.volume().descriptor().format() + " destination=" + destination.volume().descriptor().format());
        }
        if (sourceX < 0 || sourceY < 0 || sourceZ < 0
                || destinationX < 0 || destinationY < 0 || destinationZ < 0
                || width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Volume copy offsets must be non-negative and extent must be positive");
        }
        requireFits("source", sourceX, sourceY, sourceZ, width, height, depth, source);
        requireFits("destination", destinationX, destinationY, destinationZ, width, height, depth, destination);
        if (source.volume() == destination.volume()
                && source.baseMipLevel() == destination.baseMipLevel()
                && boxesOverlap(sourceX, sourceY, sourceZ, destinationX, destinationY, destinationZ,
                width, height, depth)) {
            throw new IllegalArgumentException("Overlapping copy regions within the same 3D volume mip are unsupported");
        }
    }

    public VolumeCopyCommand(String label, RhiVolumeSubresource source, RhiVolumeSubresource destination) {
        this(label, source, destination, 0, 0, 0, 0, 0, 0,
                source.width(), source.height(), source.depth());
        if (source.width() != destination.width()
                || source.height() != destination.height()
                || source.depth() != destination.depth()) {
            throw new IllegalArgumentException("Whole-subresource volume copy requires equal source/destination extents");
        }
    }

    public VolumeCopyCommand(String label, RhiVolumeView source, RhiVolumeView destination) {
        this(label,
                RhiVolumeSubresource.mip(source.volume(), requireSingleMip(source)),
                RhiVolumeSubresource.mip(destination.volume(), requireSingleMip(destination)));
    }

    private static int requireSingleMip(RhiVolumeView view) {
        if (view == null) throw new IllegalArgumentException("view");
        view.requireValid();
        if (view.descriptor().mipLevels() != 1) {
            throw new IllegalArgumentException("Volume copy view convenience constructor requires a single-mip view");
        }
        return view.descriptor().baseMipLevel();
    }

    private static boolean boxesOverlap(int sourceX, int sourceY, int sourceZ,
                                        int destinationX, int destinationY, int destinationZ,
                                        int width, int height, int depth) {
        return sourceX < destinationX + width && destinationX < sourceX + width
                && sourceY < destinationY + height && destinationY < sourceY + height
                && sourceZ < destinationZ + depth && destinationZ < sourceZ + depth;
    }

    private static void requireFits(String role, int x, int y, int z,
                                    int width, int height, int depth, RhiVolumeSubresource subresource) {
        if (x > subresource.width() - width || y > subresource.height() - height || z > subresource.depth() - depth) {
            throw new IllegalArgumentException("Volume copy " + role + " region exceeds subresource extent "
                    + subresource.width() + "x" + subresource.height() + "x" + subresource.depth());
        }
    }
}
