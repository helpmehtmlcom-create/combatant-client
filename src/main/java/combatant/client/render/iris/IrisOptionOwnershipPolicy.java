/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import combatant.client.config.MainConfig;
import combatant.client.render.iris.patch.ShaderPatchEngine;

/** Non-destructive adapter option visibility policy; stored option values are never changed. */
public enum IrisOptionOwnershipPolicy {
    ;
    public static boolean hide(String optionId) {
        if (optionId == null) return false;
        IrisRuntimeSnapshot runtime = IrisRuntime.snapshot();
        ShaderPatchEngine.ShaderpackProfile profile = ShaderPatchEngine.profile(runtime.shaderpackName());
        ShaderPatchEngine.ShaderpackIntegration adapter = profile.integration();
        if (!profile.matched()) return false;
        if (adapter.aaOptions().contains(optionId)) {
            IrisAaIntegrationState aa = IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode());
            if ((aa.owner() == IrisAaOwner.COMBATANT_TAA || aa.owner() == IrisAaOwner.COMBATANT_MSAA)
                    && aa.nativeTemporalBypass()) return true;
        }
        if (adapter.motionBlurOptions().contains(optionId)) {
            return ShaderPatchEngine.manifestApplied(profile.manifestId())
                    && IrisCompatibilityGuards.suppressShaderpackMotionBlur();
        }
        if (adapter.depthOfFieldOptions().contains(optionId)) {
            return IrisCompatibilityGuards.suppressShaderpackDepthOfField();
        }
        if (adapter.postFxOptions().contains(optionId)) {
            return IrisCompatibilityGuards.suppressShaderpackPostFx();
        }
        return false;
    }
}
