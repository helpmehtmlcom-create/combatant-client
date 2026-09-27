/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.compat;

import combatant.client.util.logging.DebugLog;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Resilient compatibility bridge for the EntityCulling mod.
 *
 * <p>Prevents culled entities from vanishing inside Shader ESP passes. Uses
 * dynamic lookup and MethodHandles to decouple from specific mod versions and
 * prevent LinkageError/NoClassDefFoundError crashes on the Render Thread.</p>
 */
public enum EntityCullingCompat {
    ;

    private static final String MOD_ID = "entityculling";
    private static final String[] CANDIDATE_CULLABLE_CLASSES = {
            "dev.tr7zw.entityculling.versionless.access.Cullable",
            "dev.tr7zw.entityculling.access.Cullable",
            "dev.tr7zw.entityculling.Cullable"
    };

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static Class<?> cullableClass;
    private static MethodHandle setCulledHandle;
    private static MethodHandle setOutOfCameraHandle;
    private static MethodHandle setTimeoutHandle;

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
                return;
            }

            ClassLoader cl = EntityCullingCompat.class.getClassLoader();
            Class<?> foundClass = null;
            for (String candidate : CANDIDATE_CULLABLE_CLASSES) {
                try {
                    foundClass = Class.forName(candidate, false, cl);
                    if (foundClass != null) {
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }

            if (foundClass == null) {
                DebugLog.infoOnce("entityculling-class-missing",
                        "[EntityCullingCompat] entityculling mod detected, but no Cullable interface matched candidate paths");
                return;
            }

            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Method setCulledMethod = findMethod(foundClass, "setCulled", boolean.class);
            Method setOutOfCameraMethod = findMethod(foundClass, "setOutOfCamera", boolean.class);
            Method setTimeoutMethod = findMethod(foundClass, "setTimeout");

            if (setCulledMethod != null) {
                try {
                    setCulledMethod.setAccessible(true);
                    setCulledHandle = lookup.unreflect(setCulledMethod);
                } catch (Throwable t) {
                    DebugLog.warnOnce("entityculling-setCulled-unreflect",
                            "[EntityCullingCompat] Failed to unreflect setCulled", t);
                }
            }

            if (setOutOfCameraMethod != null) {
                try {
                    setOutOfCameraMethod.setAccessible(true);
                    setOutOfCameraHandle = lookup.unreflect(setOutOfCameraMethod);
                } catch (Throwable t) {
                    DebugLog.warnOnce("entityculling-setOutOfCamera-unreflect",
                            "[EntityCullingCompat] Failed to unreflect setOutOfCamera", t);
                }
            }

            if (setTimeoutMethod != null) {
                try {
                    setTimeoutMethod.setAccessible(true);
                    setTimeoutHandle = lookup.unreflect(setTimeoutMethod);
                } catch (Throwable ignored) {
                }
            }

            if (setCulledHandle != null) {
                cullableClass = foundClass;
                available = true;
                DebugLog.infoOnce("entityculling-compat-initialized",
                        "[EntityCullingCompat] Successfully hooked into %s for Shader ESP", foundClass.getName());
            }
        } catch (Throwable t) {
            DebugLog.warnOnce("entityculling-init-failure",
                    "[EntityCullingCompat] Failed to initialize EntityCulling bridge", t);
        }
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getDeclaredMethod(name, parameterTypes);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void forceVisibleForShaderEsp(Entity entity) {
        if (entity == null) {
            return;
        }
        if (!initialized) {
            init();
        }
        if (!available || cullableClass == null) {
            return;
        }

        try {
            if (cullableClass.isInstance(entity)) {
                if (setCulledHandle != null) {
                    setCulledHandle.invoke(entity, false);
                }
                if (setOutOfCameraHandle != null) {
                    setOutOfCameraHandle.invoke(entity, false);
                }
                if (setTimeoutHandle != null) {
                    setTimeoutHandle.invoke(entity);
                }
            }
        } catch (Throwable t) {
            available = false;
            DebugLog.warnOnce("entityculling-runtime-failure",
                    "[EntityCullingCompat] Error invoking Cullable methods, disabling bridge", t);
        }
    }
}
