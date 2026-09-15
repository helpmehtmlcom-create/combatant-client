/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.postprocess;

import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.shader.RhiResourceOwnershipException;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.logging.DebugMode;

/** Runtime-selectable execution policy for effects with both compute and raster implementations. */
public enum PostProcessExecutionPolicy {
    AUTO,
    COMPUTE,
    RASTER;

    private static volatile PostProcessExecutionPolicy current = parseDefault();

    public static PostProcessExecutionPolicy current() {
        return current;
    }

    public static void set(PostProcessExecutionPolicy policy) {
        current = policy != null ? policy : AUTO;
    }

    public static boolean computeAvailable(CombatantRhi rhi) {
        if (rhi == null) return false;
        var caps = rhi.capabilities();
        return caps.nativeComputeSubmission() && caps.imageLoadStore();
    }

    public static boolean useCompute(CombatantRhi rhi) {
        if (current == RASTER) return false;
        if (rhi == null) return false;

        var caps = rhi.capabilities();
        boolean available = caps.nativeComputeSubmission() && caps.imageLoadStore();
        if (!available && warningLogEnabled()) {
            DebugLog.warnOnce(
                    "render-effects-compute-capability-fallback",
                    "Compute effects unavailable on this backend (nativeComputeSubmission={}, imageLoadStore={}); using raster fallbacks",
                    caps.nativeComputeSubmission(),
                    caps.imageLoadStore());
        }
        return available;
    }

    public static void logComputeActive(String key, String effectName) {
        if (!infoLogEnabled()) return;
        DebugLog.infoOnce(
                "render-effects-compute-active-" + key,
                "{}: compute path active",
                effectName);
    }

    public static boolean isTransientBackendResourceMismatch(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RhiResourceOwnershipException) return true;
            current = current.getCause();
        }
        return false;
    }

    public static void warnTransientResourceFallback(String key, String effectName, Throwable failure) {
        if (!warningLogEnabled()) return;
        DebugLog.warnOnce(
                "render-effects-compute-transient-resource-fallback-" + key,
                "{} compute path saw a stale resource from another backend generation; using raster for this invocation and retrying compute later",
                effectName,
                failure);
    }

    public static void warnRuntimeFallback(String key, String effectName, Throwable failure) {
        if (!warningLogEnabled()) return;
        DebugLog.warnOnce(
                "render-effects-compute-runtime-fallback-" + key,
                "{} compute path failed; using raster fallback for this backend session",
                effectName,
                failure);
    }

    private static boolean warningLogEnabled() {
        DebugMode mode = DebugLog.mode();
        return mode != DebugMode.OFF && mode != DebugMode.ERROR_ONLY;
    }

    private static boolean infoLogEnabled() {
        DebugMode mode = DebugLog.mode();
        return mode == DebugMode.INFO || mode == DebugMode.ALL;
    }

    private static PostProcessExecutionPolicy parseDefault() {
        String value = System.getProperty("combatant.render.effects.execution", "auto").trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "compute" -> COMPUTE;
            case "raster" -> RASTER;
            default -> AUTO;
        };
    }
}
