/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.script;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the canonical script-side UI API used to bootstrap Javet runtimes. */
enum UiScriptHostApiSource {
    ;

    private static final String RESOURCE_PATH = "/assets/combatant/ui/api/ui.js";
    private static final String MODULE_EXPORT = "export const ui =";
    private static final String GLOBAL_ASSIGNMENT = "globalThis.ui =";

    /**
     * Returns {@code ui.js} rewritten only at its export boundary so the same source can be
     * installed as the runtime-global {@code ui}. The resource file is the single implementation
     * source; Java must not carry a second copy of the factory.
     */
    static String executableSource() {
        return SourceHolder.SOURCE;
    }

    private static String loadCanonicalSource() {
        try (InputStream stream = UiScriptHostApiSource.class.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                throw new IllegalStateException("Missing canonical UI script API resource: " + RESOURCE_PATH);
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            int exportIndex = source.indexOf(MODULE_EXPORT);
            if (exportIndex < 0) {
                throw new IllegalStateException(
                        "Canonical UI script API must declare `export const ui =`: " + RESOURCE_PATH
                );
            }
            if (source.indexOf(MODULE_EXPORT, exportIndex + MODULE_EXPORT.length()) >= 0) {
                throw new IllegalStateException(
                        "Canonical UI script API declares `ui` more than once: " + RESOURCE_PATH
                );
            }
            return source.substring(0, exportIndex)
                    + GLOBAL_ASSIGNMENT
                    + source.substring(exportIndex + MODULE_EXPORT.length());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read canonical UI script API: " + RESOURCE_PATH, e);
        }
    }

    private static final class SourceHolder {
        private static final String SOURCE = loadCanonicalSource();
    }
}
