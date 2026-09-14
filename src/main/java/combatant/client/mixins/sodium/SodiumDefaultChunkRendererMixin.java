/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.sodium.SodiumSecondaryTerrainContext;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.joml.Vector4fc;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/** Connects Sodium's already-batched terrain phase to Combatant's geometry attachments. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class SodiumDefaultChunkRendererMixin {
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"
            )
    )
    private RenderPass combatant$openDeferredTerrainPass(
            CommandEncoder encoder,
            Supplier<String> label,
            GpuTextureView color,
            Optional<Vector4fc> clearColor,
            GpuTextureView depth,
            OptionalDouble clearDepth,
            Operation<RenderPass> original,
            @Local(argsOnly = true) TerrainRenderPass terrainPass
    ) {
        SodiumSecondaryTerrainContext.State secondary = SodiumSecondaryTerrainContext.current();
        if (secondary != null) {
            return secondary.openPass(encoder, label);
        }
        if (terrainPass.isTranslucent() || !CombatantRenderSystem.deferredWorld().enabled()) {
            return original.call(encoder, label, color, clearColor, depth, clearDepth);
        }
        return CombatantRenderSystem.deferredWorld().openGeometryPass(
                encoder, label, color, clearColor, depth, clearDepth
        );
    }
    @ModifyExpressionValue(
            method = "render",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;useBlockFaceCulling:Z",
                    opcode = Opcodes.GETFIELD)
    )
    private boolean combatant$disablePrimaryCameraFaceCullingForSecondaryView(boolean original) {
        return SodiumSecondaryTerrainContext.active() ? false : original;
    }

}
