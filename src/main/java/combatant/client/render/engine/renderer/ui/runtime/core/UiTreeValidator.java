/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.core;

import combatant.client.render.engine.renderer.ui.runtime.debug.UiRuntimeValidation;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/** Development-time structural checks for declarative trees before reconciliation mutates state. */
final class UiTreeValidator {
    private UiTreeValidator() {
    }

    static void validate(UiNodeSpec root) {
        if (!UiRuntimeValidation.enabled() || root == null) return;
        validateNode(root, root.key().isBlank() ? root.type().name() : root.key());
    }

    private static void validateNode(UiNodeSpec parent, String path) {
        ObjectOpenHashSet<String> siblingKeys = new ObjectOpenHashSet<>(parent.children().size());
        for (int i = 0; i < parent.children().size(); i++) {
            UiNodeSpec child = parent.children().get(i);
            if (child == null) {
                throw UiRuntimeValidation.invalid("Null UI child at " + path + "[" + i + "].");
            }

            String key = child.key();
            if (key != null && !key.isBlank() && !siblingKeys.add(key)) {
                throw UiRuntimeValidation.invalid(
                        "Duplicate sibling UI key '" + key + "' under " + path + ". Keys used for reconciliation must be unique among siblings."
                );
            }

            String childPath = path + "/" + (key != null && !key.isBlank() ? key : child.type().name() + "[" + i + "]");
            validateNode(child, childPath);
        }
    }
}
