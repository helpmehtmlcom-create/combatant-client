/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources.asset;

import combatant.client.events.UsedImplicitly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a static asset lifecycle hook included in the generated component index. */
@UsedImplicitly
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AssetLoad {
    AssetLoadPhase[] value() default AssetLoadPhase.RELOAD;

    /**
     * Optional runtime scope. Scoped hooks only participate in ordinary reload phases while the
     * scope is active, and can be activated/deactivated explicitly after client startup.
     */
    String scope() default "";

    int order() default 0;
}
