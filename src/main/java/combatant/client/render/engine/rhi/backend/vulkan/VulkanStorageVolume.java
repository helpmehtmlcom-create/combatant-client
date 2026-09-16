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
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
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
    private final long imageView;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean generalLayout;

    VulkanStorageVolume(IVulkanBackendInfo backend, StorageVolumeDescriptor descriptor) {
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
        validateImageSupport(backend, descriptor);

        long createdImage = 0L;
        long createdAllocation = 0L;
        long createdView = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK_IMAGE_TYPE_3D)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .format(VulkanConst.toVk(descriptor.format()))
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .samples(VK_SAMPLE_COUNT_1_BIT);
            imageInfo.extent().set(descriptor.width(), descriptor.height(), descriptor.depth());

            VmaAllocationCreateInfo allocationInfo = VmaAllocationCreateInfo.calloc(stack)
                    .usage(VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE);
            LongBuffer pImage = stack.mallocLong(1);
            PointerBuffer pAllocation = stack.mallocPointer(1);
            int createResult = vmaCreateImage(allocator, imageInfo, allocationInfo,
                    pImage, pAllocation, null);
            if (createResult != VK_SUCCESS) {
                throw new IllegalStateException("vmaCreateImage(3D) failed: VkResult=" + createResult
                        + " label=" + descriptor.label());
            }
            createdImage = pImage.get(0);
            createdAllocation = pAllocation.get(0);

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(createdImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_3D)
                    .format(VulkanConst.toVk(descriptor.format()));
            viewInfo.subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            LongBuffer pView = stack.mallocLong(1);
            int viewResult = vkCreateImageView(device, viewInfo, null, pView);
            if (viewResult != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView(3D) failed: VkResult=" + viewResult
                        + " label=" + descriptor.label());
            }
            createdView = pView.get(0);
        } catch (RuntimeException | Error t) {
            if (createdView != 0L) vkDestroyImageView(device, createdView, null);
            if (createdImage != 0L && createdAllocation != 0L) {
                vmaDestroyImage(allocator, createdImage, createdAllocation);
            }
            throw t;
        }

        this.image = createdImage;
        this.allocation = createdAllocation;
        this.imageView = createdView;
    }

    private static void validateImageSupport(IVulkanBackendInfo backend, StorageVolumeDescriptor descriptor) {
        VulkanPhysicalDevice physicalDevice = backend.combatant$physicalDevice();
        if (physicalDevice == null) {
            throw new IllegalStateException("Mojang Vulkan physical-device bridge is unavailable");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageFormatProperties properties = VkImageFormatProperties.calloc(stack);
            int usage = VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            int result = vkGetPhysicalDeviceImageFormatProperties(
                    physicalDevice.vkPhysicalDevice(),
                    VulkanConst.toVk(descriptor.format()),
                    VK_IMAGE_TYPE_3D,
                    VK_IMAGE_TILING_OPTIMAL,
                    usage,
                    0,
                    properties
            );
            if (result != VK_SUCCESS) {
                throw new UnsupportedOperationException("Vulkan 3D storage image format is unsupported: format="
                        + descriptor.format() + " VkResult=" + result);
            }
            VkExtent3D max = properties.maxExtent();
            if (descriptor.width() > max.width()
                    || descriptor.height() > max.height()
                    || descriptor.depth() > max.depth()) {
                throw new UnsupportedOperationException("Vulkan 3D storage image exceeds device limit "
                        + max.width() + "x" + max.height() + "x" + max.depth()
                        + ": " + descriptor.width() + "x" + descriptor.height() + "x" + descriptor.depth());
            }
        }
    }

    @Override
    public StorageVolumeDescriptor descriptor() {
        return descriptor;
    }

    long image() {
        ensureOpen();
        return image;
    }

    long imageView() {
        ensureOpen();
        return imageView;
    }

    void ensureGeneralLayout(VkCommandBuffer commandBuffer) {
        ensureOpen();
        if (generalLayout) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcStageMask(VK_PIPELINE_STAGE_2_TOP_OF_PIPE_BIT_KHR)
                    .srcAccessMask(0L)
                    .dstStageMask(VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR)
                    .dstAccessMask(VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange()
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
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
        if (!closed.compareAndSet(false, true)) return;
        ownerDevice.createCommandEncoder().queueForDestroy(this);
    }

    @Override
    public void destroy() {
        vkDestroyImageView(device, imageView, null);
        vmaDestroyImage(allocator, image, allocation);
    }
}
