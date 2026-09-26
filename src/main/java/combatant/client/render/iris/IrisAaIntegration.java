/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris;

import combatant.client.render.iris.patch.ShaderPatchEngine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Capability/ownership policy shared by config, uniforms, runtime hooks and option UI. */
public enum IrisAaIntegration {
    ;
    public static final String FEATURE_TAA = "combatant_taa_replacement";
    public static final String FEATURE_MSAA = "combatant_msaa_replacement";

    private static volatile boolean nativeTemporalBypassApplied;
    private static final Map<String, RuntimeFailure> RUNTIME_FAILURES = new ConcurrentHashMap<>();

    public static IrisAaIntegrationState resolve(String requestedMode) {
        String mode = normalize(requestedMode);
        IrisRuntimeSnapshot runtime = IrisRuntime.snapshot();
        RUNTIME_FAILURES.entrySet().removeIf(entry -> entry.getValue().epoch() != runtime.integrationEpoch());
        if (runtime.modLoaded() && !runtime.apiAvailable()) {
            return shaderpackFallback(mode, runtime, false, false, "Iris runtime API unavailable");
        }
        if (!runtime.shaderpackInUse() || !runtime.shadersEnabled()) {
            IrisAaOwner owner = switch (mode) {
                case "taa" -> IrisAaOwner.COMBATANT_TAA;
                case "msaa" -> IrisAaOwner.COMBATANT_MSAA;
                default -> IrisAaOwner.NONE;
            };
            return new IrisAaIntegrationState(owner, mode, false, false, true, false,
                    runtime.integrationEpoch(), owner == IrisAaOwner.NONE ? "configured off" : "vanilla/sodium path");
        }

        ShaderPatchEngine.ShaderpackProfile profile = ShaderPatchEngine.profile(runtime.shaderpackName());
        ShaderPatchEngine.ShaderpackIntegration adapter = profile.integration();
        boolean adapterAvailable = profile.matched() && switch (mode) {
            case "taa" -> adapter.taaReplacement() && adapter.hasTemporalMapping();
            case "msaa" -> adapter.msaaReplacement();
            default -> adapter.taaReplacement() || adapter.msaaReplacement();
        };
        ShaderPatchEngine.PatchApplicationState patch = ShaderPatchEngine.applicationState(profile.manifestId());
        boolean patchApplied = adapterAvailable && patch.complete();
        if ("off".equals(mode)) {
            return shaderpackFallback(mode, runtime, adapterAvailable, patchApplied, "Combatant AA configured off");
        }
        if (!adapterAvailable) {
            return shaderpackFallback(mode, runtime, false, false, "unsupported shaderpack");
        }
        if (!patchApplied) {
            String reason = patch.reason().isBlank() ? "shaderpack adapter patch not applied" : patch.reason();
            return shaderpackFallback(mode, runtime, true, false, reason);
        }
        RuntimeFailure failure = RUNTIME_FAILURES.get(mode);
        if (failure != null) {
            return shaderpackFallback(mode, runtime, true, true, failure.reason());
        }
        if ("taa".equals(mode) && adapter.taaReplacement()
                && runtime.patchFeatures().contains(FEATURE_TAA)) {
            return new IrisAaIntegrationState(IrisAaOwner.COMBATANT_TAA, mode, true, true, true,
                    nativeTemporalBypassApplied, runtime.integrationEpoch(), "shaderpack HDR temporal replacement");
        }
        if ("msaa".equals(mode) && adapter.msaaReplacement()
                && runtime.patchFeatures().contains(FEATURE_MSAA)) {
            return new IrisAaIntegrationState(IrisAaOwner.COMBATANT_MSAA, mode, true, true, true,
                    nativeTemporalBypassApplied, runtime.integrationEpoch(), "shaderpack world MSAA replacement");
        }
        return shaderpackFallback(mode, runtime, true, true, "requested AA mode is not supported by this integration");
    }

    private static IrisAaIntegrationState shaderpackFallback(String mode,
                                                              IrisRuntimeSnapshot runtime,
                                                              boolean adapterAvailable,
                                                              boolean patchApplied,
                                                              String reason) {
        return new IrisAaIntegrationState(IrisAaOwner.SHADERPACK_FALLBACK, mode, true, adapterAvailable,
                patchApplied, false, runtime.integrationEpoch(), reason);
    }

    public static void markNativeTemporalBypass(boolean applied) {
        nativeTemporalBypassApplied = applied;
    }

    public static void fail(String reason) {
        failMode(combatant.client.config.MainConfig.get().getAntialiasing3dMode(), reason);
    }

    public static void failMode(String mode, String reason) {
        String normalized = normalize(mode);
        String detail = reason == null || reason.isBlank() ? "runtime integration failure" : reason;
        RUNTIME_FAILURES.put(normalized, new RuntimeFailure(detail, IrisRuntime.integrationEpoch()));
        if (normalized.equals(normalize(combatant.client.config.MainConfig.get().getAntialiasing3dMode()))) {
            nativeTemporalBypassApplied = false;
        }
    }

    public static void resetRuntimeState() {
        nativeTemporalBypassApplied = false;
        RUNTIME_FAILURES.clear();
    }

    public static boolean ownsShaderpackReplacement(String requestedMode) {
        IrisAaOwner owner = resolve(requestedMode).owner();
        return owner == IrisAaOwner.COMBATANT_TAA || owner == IrisAaOwner.COMBATANT_MSAA;
    }

    private static String normalize(String value) {
        if (value == null) return "off";
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("taa") || normalized.equals("msaa") ? normalized : "off";
    }

    private record RuntimeFailure(String reason, long epoch) { }
}
