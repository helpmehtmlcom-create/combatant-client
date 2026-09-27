/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import combatant.client.features.module.modules.visuals.MotionBlur;
import combatant.client.features.module.modules.visuals.PostFX;
import combatant.client.features.module.modules.visuals.ReimaginedVisual;
import combatant.client.render.iris.patch.ShaderPatchEngine;
import combatant.client.runtime.RuntimeGate;

public enum IrisCompatibilityGuards {
    ;

    public static boolean suppressShaderpackMotionBlur() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        return IrisRuntime.isShaderpackRendererActive()
                && IrisRuntime.supports(IrisCompatibilityFeature.MOTION_BLUR_POLICY)
                && MotionBlur.isActiveStatic();
    }

    /**
     * Capability query for Combatant DoF with the active shaderpack. This must not depend on
     * transient render-resource readiness or whether Photon's optional c2 program is currently
     * compiled: Combatant renders after Iris finalization from IrisSceneDepth.
     */
    public static boolean supportsCombatantDepthOfField() {
        if (!IrisRuntime.isShaderpackRendererActive()) return true;
        IrisRuntimeSnapshot runtime = IrisRuntime.snapshot();
        return runtime.profile().supports(IrisCompatibilityFeature.DEPTH_OF_FIELD_POLICY)
                && "photon".equals(runtime.profile().id())
                && !runtime.patchManifestId().isBlank();
    }

    /** Whether the active Photon adapter owns/bypasses its native DoF pass when necessary. */
    public static boolean combatantOwnsShaderpackDepthOfField() {
        if (!IrisRuntime.isShaderpackRendererActive()
                || !IrisRuntime.supports(IrisCompatibilityFeature.DEPTH_OF_FIELD_POLICY)) return false;
        IrisRuntimeSnapshot runtime = IrisRuntime.snapshot();
        return ShaderPatchEngine.applicationState(runtime.patchManifestId()).preflightAccepted();
    }

    public static boolean suppressShaderpackDepthOfField() {
        return combatantOwnsShaderpackDepthOfField() && ReimaginedVisual.isDepthOfFieldRequestedStatic();
    }

    /** Photon keeps exposure/bloom/tonemap, while Combatant PostFX owns duplicate grading/vignette/CAS. */
    public static boolean suppressShaderpackPostFx() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        if (!IrisRuntime.isShaderpackRendererActive()
                || !IrisRuntime.supports(IrisCompatibilityFeature.POST_FX_POLICY)
                || !PostFX.isActiveStatic()) return false;
        IrisRuntimeSnapshot runtime = IrisRuntime.snapshot();
        String manifestId = runtime.patchManifestId();
        // Unlike DoF, these programs are part of Photon's active post chain. Only claim their UI
        // options after the exact grading/final targets were patched in this load session.
        return ShaderPatchEngine.targetApplied(manifestId, "/program/c14_color_grading.fsh")
                && ShaderPatchEngine.targetApplied(manifestId, "/program/final.fsh");
    }

    public static boolean suppressCombatantTerrainShaderOverrides() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        return IrisRuntime.isModLoaded();
    }

    public static boolean suppressIrisHandRendering() {
        // Do not cancel Iris' own hand renderer. Iris redirects vanilla hand submit to a no-op
        // while a shaderpack is active, so suppressing HandRenderer removes the visible hand entirely.
        // Chams builds its mask as an extra pass and must not own the visible hand path.
        return false;
    }

    public static boolean deferIrisFinalizationForSecondHandScene() {
        if (!RuntimeGate.canRunShaderBridge()) return false;
        return IrisRuntime.isShaderpackRendererActive();
    }
}
