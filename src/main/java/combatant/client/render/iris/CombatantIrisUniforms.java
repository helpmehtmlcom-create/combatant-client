/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.iris;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.joml.Vector3f;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.visuals.FullBright;
import combatant.client.features.module.modules.visuals.WorldTweaks;
import combatant.client.render.engine.RenderState;
import combatant.client.config.MainConfig;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.render.engine.light.LightmapState;
import combatant.client.render.engine.temporal.TemporalJitterSequence;
import org.joml.Vector2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public enum CombatantIrisUniforms {
    ;
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant");
    private static final Map<AaProgramKey, AaUniformLocations> AA_UNIFORM_LOCATIONS = new ConcurrentHashMap<>();
    private static final Set<AaDiagnosticKey> LOGGED_AA_PROGRAMS = ConcurrentHashMap.newKeySet();
    private static boolean loggedRegistration;

    public static void add(UniformHolder uniforms) {
        if (!loggedRegistration) {
            loggedRegistration = true;
            LOGGER.info("[IrisPatch] registering Combatant Iris uniforms via CommonUniforms.addNonDynamicUniforms");
        }
        uniforms
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantFullbrightEnabled", CombatantIrisUniforms::fullbrightEnabled)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantFullbrightMinLight", CombatantIrisUniforms::fullbrightMinLight)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantAmbientLight", LightmapState::getAmbient)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantBaseAmbientLight", LightmapState::getBaseAmbient)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantOverrideAmbientLight", CombatantIrisUniforms::overrideAmbient)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogControl", CombatantIrisUniforms::fogControlEnabled)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogModify", CombatantIrisUniforms::fogModifyEnabled)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogStart", CombatantIrisUniforms::fogStart)
                .uniform1f(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogEnd", CombatantIrisUniforms::fogEnd)
                .uniform3f(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogColor", CombatantIrisUniforms::fogColor)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksFogDisableMask", CombatantIrisUniforms::fogDisableMask)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantEyeInWater", CombatantIrisUniforms::eyeInWater)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksTimeOverride", CombatantIrisUniforms::timeOverride)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksTimeTicks", CombatantIrisUniforms::timeTicks)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantWorldTweaksWeatherMode", CombatantIrisUniforms::weatherMode)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantSuppressShaderpackMotionBlur", IrisCompatibilityGuards::suppressShaderpackMotionBlur)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantSuppressShaderpackDepthOfField", IrisCompatibilityGuards::suppressShaderpackDepthOfField)
                .uniform1b(UniformUpdateFrequency.PER_FRAME, "combatantSuppressShaderpackPostFx", IrisCompatibilityGuards::suppressShaderpackPostFx)
                .uniform1i(UniformUpdateFrequency.PER_FRAME, "combatantAaOwner", CombatantIrisUniforms::aaOwner)
                .uniform2f(UniformUpdateFrequency.PER_FRAME, "combatantTaaOffset", CombatantIrisUniforms::taaOffset);
    }

    /** Writes AA ownership after Iris has bound the concrete shaderpack program. */
    public static void applyAaToCurrentProgram() {
        applyAaToCurrentProgram("ProgramUniforms.update");
    }

    /**
     * Same write as {@link #applyAaToCurrentProgram()}, with a diagnostic source label.
     * ExtendedShader has a custom-uniform push after ProgramUniforms.update(), so its RETURN hook
     * calls this once more to make Combatant ownership the final value seen by the draw.
     */
    public static void applyAaToCurrentProgram(String source) {
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        if (program <= 0) return;

        long epoch = IrisRuntime.integrationEpoch();
        AaProgramKey programKey = new AaProgramKey(epoch, program);
        AaUniformLocations locations = AA_UNIFORM_LOCATIONS.computeIfAbsent(programKey, ignored ->
                new AaUniformLocations(
                        GL20C.glGetUniformLocation(program, "combatantAaOwner"),
                        GL20C.glGetUniformLocation(program, "combatantTaaOffset"),
                        GL20C.glGetUniformLocation(program, "taa_offset")
                ));

        int owner = aaOwner();
        if (locations.owner() >= 0) {
            GL20C.glUniform1i(locations.owner(), owner);
        }
        if (locations.taaOffset() >= 0) {
            Vector2f offset = taaOffset(owner);
            GL20C.glUniform2f(locations.taaOffset(), offset.x, offset.y);
        }
        if (owner == 1 && locations.owner() >= 0 && locations.shaderpackTaaOffset() >= 0) {
            // Photon owns a separate custom uniform named taa_offset. Only touch it when this
            // linked program also contains Combatant's AA-owner uniform; that keeps unrelated
            // shaderpacks which happen to use the same taa_offset name out of this contract.
            // CustomUniforms.push runs after ProgramUniforms.update(), so the RETURN hooks must
            // zero the real linked-program value as well as combatantTaaOffset for MSAA.
            GL20C.glUniform2f(locations.shaderpackTaaOffset(), 0.0f, 0.0f);
        }

        // Read back only once per concrete GL program/owner/epoch. glGetUniform* is a synchronous
        // driver query, so doing it on every ProgramUniforms.update() would turn diagnostics into
        // a render-thread stall. The direct glUniform* writes above still happen every update.
        AaDiagnosticKey diagnosticKey = new AaDiagnosticKey(epoch, program, owner);
        if (LOGGED_AA_PROGRAMS.add(diagnosticKey)) {
            int readbackOwner = locations.owner() >= 0
                    ? GL20C.glGetUniformi(program, locations.owner())
                    : Integer.MIN_VALUE;
            LOGGER.info(
                    "[IrisAA/GL] source={} epoch={} program={} ownerLocation={} writtenOwner={} readbackOwner={} taaOffsetLocation={} shaderpackTaaOffsetLocation={}",
                    source == null ? "unknown" : source, epoch, program, locations.owner(), owner, readbackOwner,
                    locations.taaOffset(), locations.shaderpackTaaOffset()
            );
        }
    }

    private static boolean fullbrightEnabled() {
        FullBright fullBright = Modules.get(FullBright.class);
        return fullBright != null && fullBright.isEnabled();
    }

    private static float fullbrightMinLight() {
        FullBright fullBright = Modules.get(FullBright.class);
        return fullBright != null ? fullBright.getMinLight() / 15.0f : 0.0f;
    }

    private static float overrideAmbient() {
        return Math.max(0.0f, LightmapState.getOverrideAmbient());
    }

    private static WorldTweaks worldTweaks() {
        return Modules.get(WorldTweaks.class);
    }

    private static boolean fogControlEnabled() {
        WorldTweaks module = worldTweaks();
        return module != null && module.isFogControlEnabled();
    }

    private static boolean fogModifyEnabled() {
        WorldTweaks module = worldTweaks();
        return module != null && module.isFogModifyEnabled();
    }

    private static float fogStart() {
        WorldTweaks module = worldTweaks();
        return module != null ? module.getFogStartBlocks() : 0.0f;
    }

    private static float fogEnd() {
        WorldTweaks module = worldTweaks();
        return module != null ? module.getFogEndBlocks() : 0.0f;
    }

    private static Vector3f fogColor() {
        WorldTweaks module = worldTweaks();
        int argb = module != null ? module.getFogColorArgb() : 0xFFFFFFFF;
        return new Vector3f(
                ((argb >> 16) & 0xFF) / 255.0f,
                ((argb >> 8) & 0xFF) / 255.0f,
                (argb & 0xFF) / 255.0f
        );
    }

    private static int fogDisableMask() {
        WorldTweaks module = worldTweaks();
        if (module == null || !module.isFogControlEnabled()) {
            return 0;
        }
        int mask = 0;
        if (module.disableWaterFog()) mask |= 1;
        if (module.disableLavaFog()) mask |= 1 << 1;
        if (module.disablePowderSnowFog()) mask |= 1 << 2;
        if (module.disableOverworldFog()) mask |= 1 << 3;
        if (module.disableNetherFog()) mask |= 1 << 4;
        if (module.disableEndFog()) mask |= 1 << 5;
        if (module.disableDistanceFog()) mask |= 1 << 6;
        if (module.disableWeatherFog()) mask |= 1 << 7;
        return mask;
    }

    private static int eyeInWater() {
        FogType submersion = RenderState.cameraSubmersion;
        if (submersion == FogType.WATER) return 1;
        if (submersion == FogType.LAVA) return 2;
        if (submersion == FogType.POWDER_SNOW) return 3;
        return 0;
    }

    private static boolean timeOverride() {
        WorldTweaks module = worldTweaks();
        return module != null && module.shouldOverrideTime();
    }

    private static int timeTicks() {
        WorldTweaks module = worldTweaks();
        if (module != null && module.shouldOverrideTime()) {
            return module.getTimeOfDayTicks();
        }
        Minecraft client = Minecraft.getInstance();
        return client != null && client.level != null ? (int) (client.level.getLevelData().getGameTime() % 24000L) : 0;
    }

    private static int weatherMode() {
        WorldTweaks module = worldTweaks();
        if (module != null && module.isWeatherControlEnabled()) {
            return switch (module.getWeatherMode()) {
                case RAIN -> 1;
                case THUNDER -> 2;
                default -> 0;
            };
        }
        Minecraft client = Minecraft.getInstance();
        Level world = client != null ? client.level : null;
        if (world == null) return 0;
        if (WorldTweaks.isServerThundering(world)) return 2;
        return WorldTweaks.isServerRaining(world) ? 1 : 0;
    }

    private static int aaOwner() {
        IrisAaOwner owner = IrisAaIntegration.resolve(MainConfig.get().getAntialiasing3dMode()).owner();
        return switch (owner) {
            case COMBATANT_MSAA -> 1;
            case COMBATANT_TAA -> 2;
            default -> 0;
        };
    }

    private static Vector2f taaOffset() {
        return taaOffset(aaOwner());
    }

    private static Vector2f taaOffset(int owner) {
        if (owner != 2 || IrisRuntime.isRenderingShadowPass()) return new Vector2f();
        var frame = CombatantRenderSystem.currentContext();
        if (frame == null) return new Vector2f();
        Minecraft client = Minecraft.getInstance();
        int width = client != null && client.gameRenderer != null && client.gameRenderer.mainRenderTarget() != null
                ? Math.max(1, client.gameRenderer.mainRenderTarget().width) : 1;
        Vector2f sample = TemporalJitterSequence.sample(frame.frameId());
        // Final NDC offset used by Combatant TAA. Keep shaderpack rasterization, depth reconstruction,
        // SSRT/SSR screen-space conversions and imported geometry on exactly the same sequence.
        Vector2f uvOffset = TemporalJitterSequence.uvOffset(sample, width);
        return new Vector2f(uvOffset.x * 2.0f, uvOffset.y * 2.0f);
    }
    private record AaProgramKey(long epoch, int program) {}

    private record AaUniformLocations(int owner, int taaOffset, int shaderpackTaaOffset) {}

    private record AaDiagnosticKey(long epoch, int program, int owner) {}

}
