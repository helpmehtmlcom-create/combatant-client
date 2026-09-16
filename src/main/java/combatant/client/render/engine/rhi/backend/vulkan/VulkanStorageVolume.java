/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.vulkan;

import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiValidation;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
import combatant.client.util.logging.DebugLog;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.VK12.*;

/** VkImage 3D allocation using the exact VkDevice/VMA allocator owned by Mojang. */
final class VulkanStorageVolume implements RhiStorageVolume, Destroyable {
    private final StorageVolumeDescriptor descriptor;
    private final VulkanDevice ownerDevice;
    private final VkDevice device;
    private final long allocator;
    private final long image;
    private final long allocation;
    private final long[] imageViews;
    private final RhiStats stats;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean generalLayout;

    VulkanStorageVolume(IVulkanBackendInfo backend, StorageVolumeDescriptor descriptor, RhiStats stats) {
        if (backend == null || backend.combatant$vma() == 0L || backend.combatant$vkDevice() == null) {
            throw new IllegalStateException("Mojang Vulkan device/VMA bridge is unavailable");
        }
        if (!(backend instanceof VulkanDevice vulkanDevice)) {
            throw new IllegalStateException("Vulkan backend bridge is not the active Mojang VulkanDevice");
        }
        this.descriptor = descriptor;
        this.ownerDevice = vulkanDevice;
        this.device = backend.combatant$vkDevice();
        this.allocator = backend.combatant$vma();
        this.stats = stats;
        int usage = imageUsage(descriptor);
        validateImageSupport(backend, descriptor, usage);

        long createdImage = 0L;
        long createdAllocation = 0L;
        long[] createdViews = new long[descriptor.mipLevels()];
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_3D)
                    .mipLevels(descriptor.mipLevels())
                    .arrayLayers(1)
                    .format(VulkanConst.toVk(descriptor.format()))
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            imageInfo.extent().set(descriptor.width(), descriptor.height(), descriptor.depth());

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer pImage = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            int createResult = vmaCreateImage(allocator, imageInfo, allocationInfo, pImage, pAllocation, null);
            if (createResult != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateImage(3D) failed: VkResult=" + createResult
                        + " label=" + descriptor.label());
            }
            createdImage = pImage.get(0);
            createdAllocation = pAllocation.get(0);

            for (int mip = 0; mip < descriptor.mipLevels(); mip++) {
                VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(createdImage)
                        .viewType(VK_IMAGE_VIEW_TYPE_3D)
                        .format(VulkanConst.toVk(descriptor.format()));
                viewInfo.subresourceRange()
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(mip)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1);
                LongBuffer pView = stack.mallocLong(1);
                int viewResult = vkCreateImageView(device, viewInfo, null, pView);
                if (viewResult != VK_SUCCESS) {
                    throw new IllegalStateException("vkCreateImageView(3D) failed: VkResult=" + viewResult
                            + " label=" + descriptor.label() + " mip=" + mip);
                }
                createdViews[mip] = pView.get(0);
            }
        } catch (RuntimeException | Error t) {
            for (long view : createdViews) if (view != 0L) vkDestroyImageView(device, view, null);
            if (createdImage != 0L && createdAllocation != 0L) {
                vmaDestroyImage(allocator, createdImage, createdAllocation);
            }
            throw t;
        }

        this.image = createdImage;
        this.allocation = createdAllocation;
        this.imageViews = createdViews;
        stats.storageVolumeAllocated(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/Vulkan] allocated storage volume '{}' {}x{}x{} mips={} format={} usage=0x{} approxBytes={}",
                descriptor.label(), descriptor.width(), descriptor.height(), descriptor.depth(), descriptor.mipLevels(),
                descriptor.format(), Integer.toHexString(usage), descriptor.approximateByteSize());
    }

    private static int imageUsage(StorageVolumeDescriptor descriptor) {
        int usage = VK_IMAGE_USAGE_STORAGE_BIT;
        if (descriptor.copySource()) usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        if (descriptor.copyDestination()) usage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT;
        return usage;
    }

    private static void validateImageSupport(IVulkanBackendInfo backend,
                                             StorageVolumeDescriptor descriptor,
                                             int usage) {
        VulkanPhysicalDevice physicalDevice = backend.combatant$physicalDevice();
        if (physicalDevice == null) {
            throw new IllegalStateException("Mojang Vulkan physical-device bridge is unavailable");
        }
        int vkFormat = VulkanConst.toVk(descriptor.format());
        if (vkFormat == VK_FORMAT_UNDEFINED) {
            throw new UnsupportedOperationException("Vulkan has no format mapping for storage volume: " + descriptor.format());
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties formatProperties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(physicalDevice.vkPhysicalDevice(), vkFormat, formatProperties);
            int optimalFeatures = formatProperties.optimalTilingFeatures();
            if ((optimalFeatures & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) == 0) {
                throw new UnsupportedOperationException("Vulkan format lacks STORAGE_IMAGE support for optimal tiling: "
                        + descriptor.format());
            }
            VkImageFormatProperties properties = VkImageFormatProperties.calloc(stack);
            int result = vkGetPhysicalDeviceImageFormatProperties(
                    physicalDevice.vkPhysicalDevice(), vkFormat, VK_IMAGE_TYPE_3D,
                    VK_IMAGE_TILING_OPTIMAL, usage, 0, properties);
            if (result != VK_SUCCESS) {
                throw new UnsupportedOperationException("Vulkan 3D storage image configuration is unsupported: format="
                        + descriptor.format() + " usage=0x" + Integer.toHexString(usage) + " VkResult=" + result);
            }
            VkExtent3D max = properties.maxExtent();
            if (descriptor.width() > max.width()
                    || descriptor.height() > max.height()
                    || descriptor.depth() > max.depth()) {
                throw new UnsupportedOperationException("Vulkan 3D storage image exceeds device limit "
                        + max.width() + "x" + max.height() + "x" + max.depth()
                        + ": " + descriptor.width() + "x" + descriptor.height() + "x" + descriptor.depth());
            }
            if (descriptor.mipLevels() > properties.maxMipLevels()) {
                throw new UnsupportedOperationException("Vulkan 3D storage image mip count exceeds device limit "
                        + properties.maxMipLevels() + ": " + descriptor.mipLevels());
            }
        }
    }

    @Override
    public StorageVolumeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    long image() {
        ensureOpen();
        return image;
    }

    long imageView(int mipLevel) {
        ensureOpen();
        if (mipLevel < 0 || mipLevel >= imageViews.length) {
            throw new IllegalArgumentException("Storage volume mip out of range: " + mipLevel + " / " + imageViews.length);
        }
        return imageViews[mipLevel];
    }

    void ensureGeneralLayout(VkCommandBuffer commandBuffer) {
        ensureOpen();
        if (generalLayout) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
            long dstAccess = 0L;
            if (descriptor.access().readable()) dstAccess |= VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR;
            if (descriptor.access().writable()) dstAccess |= VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR;
            barrier.get(0).sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT_KHR)
                    .srcAccessMask(0L)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                    .dstAccessMask(dstAccess)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(descriptor.mipLevels())
                    .baseArrayLayer(0)
                    .layerCount(1);
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack)
                    .sType$Default()
                    .pImageMemoryBarriers(barrier);
            vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
        }
        generalLayout = true;
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Storage volume is closed: " + descriptor.label());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            RhiValidation.doubleDestroy("Vulkan storage volume", descriptor.label());
            return;
        }
        ownerDevice.createCommandEncoder().queueForDestroy(this);
    }

    @Override
    public void destroy() {
        for (long view : imageViews) vkDestroyImageView(device, view, null);
        vmaDestroyImage(allocator, image, allocation);
        stats.storageVolumeDestroyed(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/Vulkan] destroyed storage volume '{}'", descriptor.label());
    }
}
