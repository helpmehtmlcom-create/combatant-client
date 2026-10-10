/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicMaterials;
import combatant.client.util.schematic.SchematicState;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.server.DonutSmp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places the missing blocks of the selected schematic layer from where the player stands, bottom-up.
 *
 * <p>With rotations on, a click only goes out once both the rotation the server already has and the one this
 * tick's movement packet will carry look at the planned face: vanilla resolves facing from the former and
 * Grim checks the click against the latter.</p>
 */
@ModuleInfo(
        id = "schematicbuilder",
        displayName = "SchematicBuilder",
        aliases = {"Printer", "LitematicaPrinter", "AutoBuild"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.schematicbuilder.description")
public final class SchematicBuilder extends Module {

    private static final int ROTATION_PRIORITY = 10;
    private static final int PLAN_TIMEOUT_TICKS = 20;
    private static final int RETRY_AFTER_TICKS = 40;
    private static final int RECENT_PLACE_TICKS = 10;
    private static final int MAX_PLAN_ATTEMPTS = 12;
    private static final int MAX_CANDIDATES = 96;
    private static final int SHORTFALL_INTERVAL = 20;
    private static final int MISSING_NOTIFY_INTERVAL = 200;
    private static final double DONUT_RANGE = 4.0;
    private static final int DONUT_MIN_DELAY = 3;
    private static final int DONUT_MAX_JITTER = 2;
    private static final float DONUT_MAX_ROTATION_SPEED = 45.0f;

    public enum ApproximateMode {
        PLACE,
        SKIP
    }

    public enum Status {
        IDLE,
        NO_SCHEMATIC,
        WORKING,
        PAUSED,
        MISSING_MATERIALS,
        OUT_OF_REACH,
        DONE
    }

    private final NumberValue<Float> range = num("range", 4.0f, 2.0f, 4.5f);
    private final NumberValue<Integer> delay = num("delay", 3, 0, 20);
    private final NumberValue<Integer> blocksPerTick = num("blocks_per_tick", 1, 1, 4);
    private final BooleanValue rotate = bool("rotate", true);
    private final NumberValue<Float> rotationSpeedMin =
            visibleWhen(num("rotation_speed_min", 30.0f, 5.0f, 180.0f), rotate::get);
    private final NumberValue<Float> rotationSpeedMax =
            visibleWhen(num("rotation_speed_max", 60.0f, 5.0f, 180.0f), rotate::get);
    private final BooleanValue silentSwap = bool("silent_swap", true);
    private final BooleanValue restock = bool("restock", true);
    private final NumberValue<Integer> restockSlot = visibleWhen(num("restock_slot", 9, 1, 9), restock::get);
    private final BooleanValue pauseWhenMissing = bool("pause_when_missing", true);
    private final BooleanValue autoAdvanceLayer = bool("auto_advance_layer", true);
    private final BooleanValue strictLayers = bool("strict_layers", true);
    private final EnumValue<ApproximateMode> approximate = enumMode("approximate_states", ApproximateMode.PLACE);
    private final BooleanValue stopOnDamage = bool("stop_on_damage", false);
    private final BooleanValue donutSmpSafe = bool("donutsmp_safe", true);
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);

    private final Minecraft mc = Minecraft.getInstance();
    private final SchematicState state = SchematicState.INSTANCE;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final NumberValue<Float> effectiveSpeedMin = new NumberValue<>("effective_speed_min", 30.0f, 1.0f, 180.0f);
    private final NumberValue<Float> effectiveSpeedMax = new NumberValue<>("effective_speed_max", 60.0f, 1.0f, 180.0f);
    private final LinearAngleSmooth smoothing =
            new LinearAngleSmooth(effectiveSpeedMin, effectiveSpeedMax, effectiveSpeedMin, effectiveSpeedMax);
    private final Map<Long, Integer> retryAfter = new HashMap<>();
    private final int[] candidateCells = new int[MAX_CANDIDATES];
    private final double[] candidateDistance = new double[MAX_CANDIDATES];

    private Pending pending;
    private int pendingAge;
    private int cooldown;
    private int ticks;
    private int placed;
    private int approximatePlaced;
    private float lastHealth = -1.0f;
    private ClientLevel lastLevel;
    private Status status = Status.IDLE;
    private int lastShortfallTick = Integer.MIN_VALUE;
    private int lastMissingNotifyTick = Integer.MIN_VALUE;
    private int completedLayerNotified = -1;
    private boolean missingThisTick;

    @Override
    public void onEnable() {
        reset();
        // Config load can enable the module before any world exists; only warn a player who can act on it.
        if (mc.player != null && !state.isPlaced()) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.no_placement"));
        }
    }

    @Override
    public void onDisable() {
        reset();
        state.setMissingMaterials(List.of());
        status = Status.IDLE;
    }

    private void reset() {
        RotationManager.INSTANCE.release(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        pending = null;
        pendingAge = 0;
        cooldown = 0;
        retryAfter.clear();
        lastHealth = -1.0f;
        lastLevel = mc.level;
        completedLayerNotified = -1;
        lastShortfallTick = Integer.MIN_VALUE;
    }

    public Status status() {
        return status;
    }

    public int placedCount() {
        return placed;
    }

    public int approximateCount() {
        return approximatePlaced;
    }

    private boolean donutRules() {
        return donutSmpSafe.get() && DonutSmp.isConnected();
    }

    private double effectiveRange() {
        double value = anticheat.get().limit((double) range.get(), AntiCheatPreset.GRIM_BLOCK_RANGE, AntiCheatPreset.VANILLA_BLOCK_RANGE);
        return donutRules() ? Math.min(value, DONUT_RANGE) : value;
    }

    private int nextDelay() {
        int value = delay.get();
        if (!donutRules()) return value;
        // A fixed cadence is the easiest pattern to spot; jitter keeps it at roughly a human pace.
        return Math.max(value, DONUT_MIN_DELAY) + ThreadLocalRandom.current().nextInt(DONUT_MAX_JITTER + 1);
    }

    private int effectiveBlocksPerTick() {
        return donutRules() ? 1 : blocksPerTick.get();
    }

    private boolean effectiveRotate() {
        return rotate.get() || donutRules() || anticheat.get() == AntiCheatPreset.GRIM;
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null) return;
        ticks++;
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        if (stopOnDamage.get() && lastHealth >= 0.0f && player.getHealth() < lastHealth - 0.01f) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.damaged"));
            setEnabled(false);
            return;
        }
        lastHealth = player.getHealth();
        state.tickDiff(mc, 256, 256);
        retryAfter.values().removeIf(until -> until <= ticks);
        if (ticks - lastShortfallTick >= SHORTFALL_INTERVAL) {
            lastShortfallTick = ticks;
            updateShortfall(player);
        }
    }

    @EventHandler(priority = -15)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() == RotationUpdateEvent.Type.PRE) {
            pre();
        } else {
            post();
        }
    }

    private boolean canWork(LocalPlayer player) {
        if (player == null || mc.level == null || mc.gameMode == null) return false;
        if (!state.isPlaced()) {
            status = Status.NO_SCHEMATIC;
            return false;
        }
        if (ClientScreen.current() != null || player.isUsingItem()) {
            status = Status.PAUSED;
            return false;
        }
        return true;
    }

    private void pre() {
        LocalPlayer player = mc.player;
        if (!canWork(player)) {
            pending = null;
            RotationManager.INSTANCE.release(this);
            return;
        }
        if (cooldown > 0) cooldown--;
        if (pending != null && (++pendingAge > PLAN_TIMEOUT_TICKS || !stillValid(player, pending))) {
            if (pendingAge > PLAN_TIMEOUT_TICKS) retryAfter.put(pending.plan.target().asLong(), ticks + RETRY_AFTER_TICKS);
            pending = null;
        }
        // Planning during the cooldown lets the rotation travel to the next block while the delay runs out.
        if (pending == null) {
            pending = choose(player);
            pendingAge = 0;
        }
        if (pending == null) {
            RotationManager.INSTANCE.release(this);
            return;
        }
        status = Status.WORKING;
        if (effectiveRotate()) requestRotation(pending.plan.rotation());
    }

    private void requestRotation(Rotation rotation) {
        float min = rotationSpeedMin.get();
        float max = Math.max(min, rotationSpeedMax.get());
        if (donutRules()) {
            min = Math.min(min, DONUT_MAX_ROTATION_SPEED);
            max = Math.min(max, DONUT_MAX_ROTATION_SPEED);
        }
        effectiveSpeedMin.set(min);
        effectiveSpeedMax.set(max);
        RotationTarget target = new RotationTarget(rotation, null, List.of(smoothing), 2, 1.0f, true,
                MovementCorrection.SILENT, null);
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private void post() {
        LocalPlayer player = mc.player;
        if (pending == null || cooldown > 0 || !canWork(player)) return;
        int budget = effectiveRotate() ? 1 : effectiveBlocksPerTick();
        for (int i = 0; i < budget && pending != null && cooldown <= 0; i++) {
            if (!tryPlace(player, pending)) return;
            pending = effectiveRotate() || i + 1 >= budget ? null : choose(player);
        }
    }

    private boolean tryPlace(LocalPlayer player, Pending work) {
        BuildPlanner.Plan plan = work.plan;
        BlockHitResult hit;
        Rotation facingRotation;
        if (effectiveRotate()) {
            Rotation current = RotationManager.INSTANCE.getCurrentRotation();
            Rotation server = RotationManager.INSTANCE.getServerRotation();
            double reach = effectiveRange() + 0.5;
            hit = BuildPlanner.trace(player, current, reach);
            if (!BuildPlanner.matches(hit, plan) || !BuildPlanner.matches(BuildPlanner.trace(player, server, reach), plan)) {
                return false;
            }
            facingRotation = server;
        } else {
            hit = new BlockHitResult(plan.hit(), plan.face(), plan.support(), false);
            facingRotation = null;
        }

        ItemStack stack = player.getInventory().getItem(work.hotbarSlot);
        if (stack.isEmpty() || stack.getItem() != work.item) {
            pending = null;
            return false;
        }
        BlockState predicted = BuildPlanner.simulate(player, mc.level, stack, plan.target(), plan.support(),
                plan.face(), hit.getLocation(), facingRotation);
        if (predicted == null || predicted.getBlock() != work.expected.getBlock()) return false;
        boolean exact = predicted == work.expected;
        // The ray reaches the planned face before the rotation settles on the planned point; clicking then puts
        // a stair or slab in the wrong half. Wait for the exact click, but not past half the plan timeout.
        if (!exact && plan.exact() && pendingAge < PLAN_TIMEOUT_TICKS / 2) return false;
        if (!exact && approximate.get() == ApproximateMode.SKIP) {
            retryAfter.put(plan.target().asLong(), ticks + RETRY_AFTER_TICKS);
            pending = null;
            return false;
        }

        selectSlot(work.hotbarSlot);
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit.getLocation(), plan.face(), plan.support(), false));
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
            placed++;
            if (!exact) approximatePlaced++;
        }
        retryAfter.put(plan.target().asLong(), ticks + RECENT_PLACE_TICKS);
        cooldown = nextDelay();
        return true;
    }

    private void selectSlot(int hotbarSlot) {
        if (silentSwap.get()) {
            InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 2);
        } else {
            InventorySwap.INSTANCE.selectHotbar(hotbarSlot);
        }
    }

    private boolean stillValid(LocalPlayer player, Pending work) {
        BlockState current = mc.level.getBlockState(work.plan.target());
        if (!current.canBeReplaced()) return false;
        if (mc.level.getBlockState(work.plan.support()).canBeReplaced()) return false;
        ItemStack stack = player.getInventory().getItem(work.hotbarSlot);
        if (stack.isEmpty() || stack.getItem() != work.item) return false;
        double reach = effectiveRange();
        return player.getEyePosition().distanceToSqr(work.plan.hit()) <= reach * reach;
    }

    private Pending choose(LocalPlayer player) {
        SchematicDiff diff = state.diff(mc.level);
        if (diff == null) return null;
        int layer = workingLayer(diff);
        if (layer < 0) return null;
        int high = strictLayers.get() ? layer : Math.max(layer, state.visibleHigh());

        int count = collectCandidates(player, diff, layer, high);
        if (count == 0) {
            status = missingThisTick ? Status.MISSING_MATERIALS : Status.OUT_OF_REACH;
            return null;
        }
        if (missingThisTick && pauseWhenMissing.get()) {
            status = Status.MISSING_MATERIALS;
            notifyMissing();
            return null;
        }
        sortCandidates(count);
        double reach = effectiveRange();
        boolean aim = effectiveRotate();
        for (int i = 0; i < Math.min(count, MAX_PLAN_ATTEMPTS); i++) {
            int cell = candidateCells[i];
            BlockState expected = diff.expectedState(cell);
            Item item = SchematicMaterials.itemFor(expected);
            int hotbar = hotbarSlotFor(player, item);
            if (hotbar < 0) {
                if (restock.get() && restockFromInventory(player, item)) return null;
                continue;
            }
            BlockPos target = diff.worldPos(cell, scratch).immutable();
            ItemStack stack = player.getInventory().getItem(hotbar);
            BuildPlanner.Plan plan = BuildPlanner.plan(player, mc.level, target, expected, stack, reach, aim);
            if (plan == null) continue;
            if (!plan.exact() && approximate.get() == ApproximateMode.SKIP) {
                retryAfter.put(target.asLong(), ticks + RETRY_AFTER_TICKS);
                continue;
            }
            return new Pending(plan, expected, item, hotbar);
        }
        status = Status.OUT_OF_REACH;
        return null;
    }

    /** The selected layer, advanced past finished layers when enabled. -1 once the whole build is done. */
    private int workingLayer(SchematicDiff diff) {
        int layer = state.layer();
        int top = state.layerCount() - 1;
        while (diff.layerComplete(layer)) {
            if (layer >= top) {
                status = Status.DONE;
                if (completedLayerNotified != Integer.MAX_VALUE) {
                    completedLayerNotified = Integer.MAX_VALUE;
                    Notifier.success(I18n.get("module.schematicbuilder.notify.done"));
                }
                return -1;
            }
            if (!autoAdvanceLayer.get()) {
                status = Status.DONE;
                if (completedLayerNotified != layer) {
                    completedLayerNotified = layer;
                    Notifier.info(I18n.get("module.schematicbuilder.notify.layer_done", layer + 1));
                }
                return -1;
            }
            layer++;
            state.setLayer(layer);
        }
        return layer;
    }

    private int collectCandidates(LocalPlayer player, SchematicDiff diff, int low, int high) {
        missingThisTick = false;
        Vec3 eyes = player.getEyePosition();
        // Cell centres up to half a block diagonal beyond reach can still expose a reachable face.
        double reach = effectiveRange() + 0.87;
        double reachSq = reach * reach;
        int count = 0;
        for (int y = low; y <= high; y++) {
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                byte status = diff.status(cell);
                if (status == SchematicDiff.CORRECT || status == SchematicDiff.WRONG) continue;
                diff.worldPos(cell, scratch);
                double dx = scratch.getX() + 0.5 - eyes.x, dy = scratch.getY() + 0.5 - eyes.y, dz = scratch.getZ() + 0.5 - eyes.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > reachSq || retryAfter.containsKey(scratch.asLong())) continue;
                if (diff.refresh(mc.level, cell) != SchematicDiff.MISSING) continue;
                BlockState expected = diff.expectedState(cell);
                Item item = SchematicMaterials.itemFor(expected);
                if (item == Items.AIR || SchematicMaterials.itemsPerCell(expected) == 0) continue;
                if (SchematicMaterials.countInInventory(player, item) == 0) {
                    missingThisTick = true;
                    continue;
                }
                if (count < MAX_CANDIDATES) {
                    candidateCells[count] = cell;
                    // Lower layers first so blocks can lean on what is below them, then farthest first: a block
                    // can only hide the faces of cells behind it, so placing near cells first walls off the far
                    // ones (the corners of a ring around the player become unclickable).
                    candidateDistance[count] = (y - low) * 1000.0 + (reachSq - distSq);
                    count++;
                }
            }
        }
        return count;
    }

    private void sortCandidates(int count) {
        for (int i = 1; i < count; i++) {
            int cell = candidateCells[i];
            double distance = candidateDistance[i];
            int j = i - 1;
            while (j >= 0 && candidateDistance[j] > distance) {
                candidateCells[j + 1] = candidateCells[j];
                candidateDistance[j + 1] = candidateDistance[j];
                j--;
            }
            candidateCells[j + 1] = cell;
            candidateDistance[j + 1] = distance;
        }
    }

    private static int hotbarSlotFor(LocalPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (inventory.getItem(selected).getItem() == item) return selected;
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    /** Swaps a stack from the main inventory into the restock slot; placement resumes on a later tick. */
    private boolean restockFromInventory(LocalPlayer player, Item item) {
        if (player.containerMenu != player.inventoryMenu) return false;
        Inventory inventory = player.getInventory();
        for (int i = 9; i < Inventory.INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).getItem() != item) continue;
            int hotbar = restockSlot.get() - 1;
            if (InventorySwap.INSTANCE.clickSwap(InventorySwap.mapInventoryToScreenSlot(i), hotbar)) {
                cooldown = Math.max(cooldown, 2);
                return true;
            }
            return false;
        }
        return false;
    }

    private void updateShortfall(LocalPlayer player) {
        SchematicDiff diff = state.currentDiff();
        Schematic schematic = state.schematic();
        if (diff == null || schematic == null) {
            state.setMissingMaterials(List.of());
            return;
        }
        int layer = state.layer();
        int high = strictLayers.get() ? layer : Math.max(layer, state.visibleHigh());
        Map<Item, int[]> needed = new IdentityHashMap<>();
        for (int y = layer; y <= high; y++) {
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                byte status = diff.status(cell);
                if (status == SchematicDiff.CORRECT || status == SchematicDiff.WRONG) continue;
                BlockState expected = diff.expectedState(cell);
                Item item = SchematicMaterials.itemFor(expected);
                int cost = SchematicMaterials.itemsPerCell(expected);
                if (item == Items.AIR || cost == 0) continue;
                needed.computeIfAbsent(item, ignored -> new int[1])[0] += cost;
            }
        }
        List<SchematicMaterials.Entry> shortfall = new ArrayList<>();
        for (Map.Entry<Item, int[]> entry : needed.entrySet()) {
            int missing = entry.getValue()[0] - SchematicMaterials.countInInventory(player, entry.getKey());
            if (missing > 0) shortfall.add(new SchematicMaterials.Entry(entry.getKey(), missing));
        }
        shortfall.sort((a, b) -> Integer.compare(b.count(), a.count()));
        state.setMissingMaterials(shortfall);
    }

    private void notifyMissing() {
        if (ticks - lastMissingNotifyTick < MISSING_NOTIFY_INTERVAL) return;
        lastMissingNotifyTick = ticks;
        List<SchematicMaterials.Entry> missing = state.missingMaterials();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < Math.min(3, missing.size()); i++) {
            if (i > 0) list.append(", ");
            SchematicMaterials.Entry entry = missing.get(i);
            list.append(entry.count()).append("x ").append(new ItemStack(entry.item()).getHoverName().getString());
        }
        if (missing.size() > 3) list.append(", ...");
        Notifier.warning(I18n.get("module.schematicbuilder.notify.missing", list.toString()));
    }

    private record Pending(BuildPlanner.Plan plan, BlockState expected, Item item, int hotbarSlot) {
    }
}
