package combatant.client.util.server;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DonutSafeModeTest {

    @Test
    void appliesOnlyToModulesNotAlreadyOnGrim() {
        DonutSafeMode m = new DonutSafeMode();
        Map<String, String> changes = m.apply(Map.of("killaura", "custom", "speed", "grim", "step", "vanilla"));
        assertEquals(Map.of("killaura", "grim", "step", "grim"), changes);
        assertTrue(m.isActive());
    }

    @Test
    void restoreSkipsModulesTheUserChangedMeanwhile() {
        DonutSafeMode m = new DonutSafeMode();
        m.apply(Map.of("killaura", "custom", "step", "vanilla"));
        Map<String, String> back = m.restore(Map.of("killaura", "grim", "step", "vulcan"));
        assertEquals(Map.of("killaura", "custom"), back);
        assertFalse(m.isActive());
    }

    @Test
    void secondApplyDoesNotOverwriteRememberedValues() {
        DonutSafeMode m = new DonutSafeMode();
        m.apply(Map.of("killaura", "custom"));
        assertTrue(m.apply(Map.of("killaura", "custom")).isEmpty());
        assertEquals(Map.of("killaura", "custom"), m.restore(Map.of("killaura", "grim")));
    }
}
