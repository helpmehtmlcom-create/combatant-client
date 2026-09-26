/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.patch;

import combatant.client.render.iris.IrisCompatibilityProfile;
import combatant.client.render.iris.IrisCompatibilityProfiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderPatchEngineProfileTest {
    @Test
    void photonRuntimeIdentityComesFromPhotonManifest() {
        ShaderPatchEngine.ShaderpackProfile selected = ShaderPatchEngine.profile("photon_v1.3b.zip");

        assertTrue(selected.matched());
        assertEquals("photon.v1_3b", selected.manifestId());
        assertEquals("photon", selected.profileId());
        assertTrue(selected.features().contains("fullbright"));
        assertTrue(selected.features().contains("combatant_taa_replacement"));
        assertTrue(selected.features().contains("combatant_msaa_replacement"));
        assertEquals("composite4", selected.integration().temporalPass());
        assertEquals(0, selected.integration().sceneColorTarget());
        assertEquals(5, selected.integration().historyTarget());
        assertTrue(selected.integration().preserveHistoryAlphaOrigin());
        assertTrue(selected.integration().taaReplacement());
        assertTrue(selected.integration().msaaReplacement());
        assertTrue(selected.integration().aaOptions().contains("TAA"));
        assertEquals(IrisCompatibilityProfile.PHOTON, IrisCompatibilityProfiles.resolve(selected, true));
    }

    @Test
    void complementaryRuntimeIdentityComesFromComplementaryManifest() {
        ShaderPatchEngine.ShaderpackProfile selected = ShaderPatchEngine.profile(
                "ComplementaryReimagined_r5.6.1.zip");

        assertTrue(selected.matched());
        assertEquals("complementary_reimagined.r5", selected.manifestId());
        assertEquals("complementary_reimagined", selected.profileId());
        assertFalse(selected.integration().taaReplacement());
        assertTrue(selected.integration().motionBlurOptions().contains("MOTION_BLUR_EFFECT"));
        assertEquals(IrisCompatibilityProfile.COMPLEMENTARY_REIMAGINED,
                IrisCompatibilityProfiles.resolve(selected, true));
    }

    @Test
    void unknownShaderpackKeepsGenericFallback() {
        ShaderPatchEngine.ShaderpackProfile selected = ShaderPatchEngine.profile("unknown-pack.zip");

        assertFalse(selected.matched());
        assertEquals(ShaderPatchEngine.ShaderpackIntegration.NONE, selected.integration());
        assertEquals(IrisCompatibilityProfile.GENERIC_IRIS,
                IrisCompatibilityProfiles.resolve(selected, true));
        assertEquals(IrisCompatibilityProfile.NONE,
                IrisCompatibilityProfiles.resolve(selected, false));
    }
}
