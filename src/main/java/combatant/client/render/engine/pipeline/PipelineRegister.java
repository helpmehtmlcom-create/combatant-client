/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.pipeline;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adds a built-in pipeline field to the compile-time render-pipeline index. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface PipelineRegister {
}
