package combatant.client.features.module.modules.combat;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.Events;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import net.minecraft.client.Minecraft;

/**
 * Standard Production Module Template for Combatant Client.
 *
 * <p>Requirements:
 * 1. Must be annotated with {@link ModuleInfo}.
 * 2. Extends {@link Module}.
 * 3. Settings registered via {@code addSetting(...)} in constructor.
 * 4. Register/unregister from {@code Events.bus()} in {@code onEnable()} / {@code onDisable()}.
 */
@ModuleInfo(
    id = "template_module",
    displayName = "Template Module",
    category = ModuleCategory.COMBAT,
    description = "Demonstrates production-ready Combatant Client module architecture.",
    enabledByDefault = false,
    aliases = {"template", "tmpl"}
)
public final class ModuleTemplate extends Module {

    // 1. Settings definitions
    private final BooleanValue silent = new BooleanValue("silent", true);
    private final NumberValue range = new NumberValue("range", 4.5, 1.0, 6.0, 0.1);
    private final NumberValue delayMs = new NumberValue("delay_ms", 50, 0, 500, 10);
    private final EnumValue<TargetMode> targetMode = new EnumValue<>("target_mode", TargetMode.SINGLE, TargetMode.class);

    public ModuleTemplate() {
        // 2. Register settings
        addSetting(silent);
        addSetting(range);
        addSetting(delayMs);
        addSetting(targetMode);

        // 3. Optional dynamic visibility constraints
        delayMs.visibleIf(silent::get);
    }

    @Override
    protected void onEnable() {
        // Subscribe to event bus
        Events.bus().register(this);
    }

    @Override
    protected void onDisable() {
        // Cleanly unsubscribe from event bus
        Events.bus().unregister(this);
    }

    @EventHandler(priority = 100)
    public void onTick(GameTickEvent event) {
        // Always verify preconditions: tick phase and valid local player
        if (event.isPost() || Minecraft.getInstance().player == null) {
            return;
        }

        // Module execution logic
    }

    public enum TargetMode {
        SINGLE,
        MULTI,
        CLOSEST
    }
}
