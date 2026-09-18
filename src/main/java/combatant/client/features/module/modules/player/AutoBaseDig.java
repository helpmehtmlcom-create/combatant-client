/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.module.HudPhase;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubCategory;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.player.inventory.InventorySwap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ModuleInfo(
        id = "autobasedig",
        displayName = "Auto Base Dig",
        description = "Automates cuboid area excavation for raiding and uncovering underground bases with totem check & auto-mend.",
        category = ModuleCategory.PLAYER,
        subCategory = ModuleSubCategory.DONUTSMP,
        aliases = {"basedigger", "cuboidminer", "autodig"}
)
public class AutoBaseDig extends Module {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static AutoBaseDig instance;

    public static AutoBaseDig getInstance() {
        return instance;
    }

    private final Minecraft mc = Minecraft.getInstance();

    private final StringValue pos1Str =
            text("autodig_pos1", "pos_1_coords", "");
    private final StringValue pos2Str =
            text("autodig_pos2", "pos_2_coords", "");
    private final ModeValue mineOrder =
            modeSetting("autodig_order", "mine_order", "Top to Bottom", "Top to Bottom", "Bottom to Top");
    private final BooleanValue autoSwitchTool =
            bool("autodig_auto_tool", "auto_tool", true);
    private final NumberValue<Double> reachRange =
            num("autodig_reach", "reach", 4.5, 2.0, 6.0);
    private final BooleanValue renderBox =
            bool("autodig_render_box", "render_box", true);

    private final BooleanValue totemCheck =
            bool("autodig_totem_check", "totem_check", true);
    private final BooleanValue autoEat =
            bool("autodig_auto_eat", "auto_eat", true);
    private final ModeValue goldenFood =
            modeSetting("autodig_food_mode", "golden_food", "Carrot", "Carrot", "Apple");
    private final NumberValue<Integer> foodSlot =
            num("autodig_food_slot", "food_slot", 3, 1, 9);

    private final BooleanValue autoMend =
            bool("autodig_auto_mend", "auto_mend", true);
    private final NumberValue<Integer> bottleSlot =
            num("autodig_bottle_slot", "bottle_slot", 2, 1, 9);

    private final BooleanValue webhook =
            bool("autodig_webhook", "webhook", false);
    private final StringValue webhookUrl =
            text("autodig_webhook_url", "webhook_url", "");

    private BlockPos currentTarget = null;
    private int mendTimer = 0;

    public AutoBaseDig() {
        instance = this;
    }

    public void setPos1(BlockPos pos) {
        pos1Str.set(pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    public void setPos2(BlockPos pos) {
        pos2Str.set(pos.getX() + "," + pos.getY() + "," + pos.getZ());
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        mendTimer = 0;
        if (mc.player != null && (pos1Str.get().isBlank() || pos2Str.get().isBlank())) {
            BlockPos p = mc.player.blockPosition();
            if (pos1Str.get().isBlank()) {
                setPos1(p);
            }
            if (pos2Str.get().isBlank()) {
                setPos2(new BlockPos(p.getX() + 5, p.getY() - 4, p.getZ() + 5));
            }
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d[AutoBaseDig] §7Configured excavation box from Pos 1 to Pos 2."));
            }
        }
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        mendTimer = 0;
    }

    public BlockPos getCurrentTarget() { return currentTarget; }
    public String getPos1() { return pos1Str.get(); }
    public String getPos2() { return pos2Str.get(); }

    @Override
    public void onRenderHudEngineForeground(combatant.client.render.engine.renderer.Renderer2D renderer,
                                            combatant.client.render.engine.text.TextRenderer textRenderer,
                                            net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                            float tickDelta) {
        if (mc.player == null) return;
        String status = currentTarget != null
                ? String.format("⛏️ Auto Base Dig: Excavating [%d, %d, %d]", currentTarget.getX(), currentTarget.getY(), currentTarget.getZ())
                : "⛏️ Auto Base Dig: Searching next block...";

        float textW = (float) textRenderer.getWidth(status);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int x = (int) ((screenW - textW) / 2);
        int y = 40;
        int pad = 5;

        renderer.roundedRect(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4.0f, 0xDD12121A);
        renderer.roundedRectStroke(x - pad, y - pad, textW + (pad * 2), 12 + (pad * 2), 4.0f, 1.0f, 0xFFFF6600);
        textRenderer.render(status, x, y, new combatant.client.render.engine.color.RenderColor(0xFFFF9933), true);
    }

    @EventHandler
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        // 1. Totem Check: keep totem in offhand
        if (totemCheck.get()) {
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.is(Items.TOTEM_OF_UNDYING)) {
                int tSlot = findItemInHotbar(Items.TOTEM_OF_UNDYING);
                if (tSlot >= 0) {
                    // Quick swap to offhand (slot 40 in container)
                    InventorySwap.INSTANCE.selectHotbar(tSlot);
                }
            }
        }

        // 2. Auto Eat: eat food if hungry or health is low
        if (autoEat.get()) {
            int hunger = mc.player.getFoodData().getFoodLevel();
            float hp = mc.player.getHealth();
            if (hunger <= 16 || hp < 18.0f) {
                int fSlot = foodSlot.get() - 1;
                if (fSlot >= 0 && fSlot < 9) {
                    ItemStack food = mc.player.getInventory().getItem(fSlot);
                    if (!food.isEmpty() && (food.is(Items.GOLDEN_CARROT) || food.is(Items.GOLDEN_APPLE) || food.is(Items.ENCHANTED_GOLDEN_APPLE))) {
                        InventorySwap.INSTANCE.selectHotbar(fSlot);
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                        return;
                    }
                }
            }
        }

        // 3. Auto Mend: repair pickaxe when durability drops below 15%
        if (autoMend.get()) {
            ItemStack main = mc.player.getMainHandItem();
            if (main.isDamageableItem()) {
                int maxDmg = main.getMaxDamage();
                int currentDmg = main.getDamageValue();
                int remaining = maxDmg - currentDmg;
                if (remaining < (maxDmg * 0.15)) {
                    int bSlot = bottleSlot.get() - 1;
                    if (bSlot >= 0 && bSlot < 9) {
                        ItemStack bottles = mc.player.getInventory().getItem(bSlot);
                        if (bottles.is(Items.EXPERIENCE_BOTTLE)) {
                            InventorySwap.INSTANCE.selectHotbar(bSlot);
                            if (mc.player.connection != null) {
                                mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), 90.0f, mc.player.onGround(), false));
                            }
                            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                            mendTimer++;
                            if (mendTimer < 15) {
                                return; // Spend a few ticks mending
                            }
                            mendTimer = 0;
                        }
                    }
                }
            }
        }

        BlockPos p1 = parsePos(pos1Str.get());
        BlockPos p2 = parsePos(pos2Str.get());
        if (p1 == null || p2 == null) return;

        double maxReachSq = reachRange.get() * reachRange.get();
        Vec3 eyes = mc.player.getEyePosition();

        if (currentTarget != null) {
            BlockState s = mc.level.getBlockState(currentTarget);
            if (s.isAir() || currentTarget.distToCenterSqr(eyes.x, eyes.y, eyes.z) > maxReachSq) {
                currentTarget = null;
            }
        }

        if (currentTarget == null) {
            List<BlockPos> candidates = getBlocksInBox(p1, p2);
            if (candidates.isEmpty()) {
                if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§d[AutoBaseDig] §aExcavation complete! Area fully mined."));
                }
                if (webhook.get() && !webhookUrl.get().isBlank()) {
                    sendWebhookComplete();
                }
                setEnabled(false);
                return;
            }

            // Sort according to Mine Order and proximity
            boolean topDown = "Top to Bottom".equalsIgnoreCase(mineOrder.get());
            candidates.sort((a, b) -> {
                int yComp = topDown ? Integer.compare(b.getY(), a.getY()) : Integer.compare(a.getY(), b.getY());
                if (yComp != 0) return yComp;
                return Double.compare(a.distToCenterSqr(eyes.x, eyes.y, eyes.z), b.distToCenterSqr(eyes.x, eyes.y, eyes.z));
            });

            for (BlockPos pos : candidates) {
                if (pos.distToCenterSqr(eyes.x, eyes.y, eyes.z) <= maxReachSq) {
                    currentTarget = pos;
                    break;
                }
            }
        }

        if (currentTarget != null) {
            BlockState state = mc.level.getBlockState(currentTarget);
            if (autoSwitchTool.get()) {
                int bestSlot = findBestTool(state);
                if (bestSlot >= 0) {
                    InventorySwap.INSTANCE.selectHotbar(bestSlot);
                }
            }

            mc.gameMode.startDestroyBlock(currentTarget, Direction.UP);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private int findItemInHotbar(net.minecraft.world.item.Item item) {
        if (mc.player == null) return -1;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return -1;
    }

    private void sendWebhookComplete() {
        String url = webhookUrl.get().trim();
        if (url.isEmpty()) return;

        String playerName = mc.player != null ? mc.player.getName().getString() : "Unknown";
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "Singleplayer";

        String json = String.format(
                "{\"content\":null,\"embeds\":[{" +
                        "\"title\":\"⛏️ Base Digging Completed!\"," +
                        "\"color\":65280," +
                        "\"description\":\"Cuboid excavation finished successfully.\\n*Combatant Client • DonutSMP Base Hunting*\"," +
                        "\"fields\":[" +
                        "{\"name\":\"📍 Pos 1\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"📍 Pos 2\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"👤 Player\",\"value\":\"`%s`\",\"inline\":true}," +
                        "{\"name\":\"🖥️ Server\",\"value\":\"`%s`\",\"inline\":true}" +
                        "]" +
                        "}]}",
                escapeJson(pos1Str.get()),
                escapeJson(pos2Str.get()),
                escapeJson(playerName),
                escapeJson(server)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(4))
                    .build();

            HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Throwable ignored) {
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private List<BlockPos> getBlocksInBox(BlockPos p1, BlockPos p2) {
        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ());

        List<BlockPos> list = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = mc.level.getBlockState(p);
                    if (!s.isAir() && !s.is(Blocks.BEDROCK)) {
                        list.add(p);
                    }
                }
            }
        }
        return list;
    }

    private int findBestTool(BlockState state) {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        float bestSpeed = 1.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private BlockPos parsePos(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String[] parts = s.split(",");
            if (parts.length == 3) {
                return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!renderBox.get() || mc.player == null) return;

        BlockPos p1 = parsePos(pos1Str.get());
        BlockPos p2 = parsePos(pos2Str.get());
        if (p1 == null || p2 == null) return;

        int minX = Math.min(p1.getX(), p2.getX());
        int maxX = Math.max(p1.getX(), p2.getX()) + 1;
        int minY = Math.min(p1.getY(), p2.getY());
        int maxY = Math.max(p1.getY(), p2.getY()) + 1;
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxZ = Math.max(p1.getZ(), p2.getZ()) + 1;

        int r = 255, g = 50, b = 150;

        // Box outline
        renderer.line(minX, minY, minZ, maxX, minY, minZ, r, g, b, 230);
        renderer.line(maxX, minY, minZ, maxX, minY, maxZ, r, g, b, 230);
        renderer.line(maxX, minY, maxZ, minX, minY, maxZ, r, g, b, 230);
        renderer.line(minX, minY, maxZ, minX, minY, minZ, r, g, b, 230);

        renderer.line(minX, maxY, minZ, maxX, maxY, minZ, r, g, b, 230);
        renderer.line(maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, 230);
        renderer.line(maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, 230);
        renderer.line(minX, maxY, maxZ, minX, maxY, minZ, r, g, b, 230);

        renderer.line(minX, minY, minZ, minX, maxY, minZ, r, g, b, 230);
        renderer.line(maxX, minY, minZ, maxX, maxY, minZ, r, g, b, 230);
        renderer.line(maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, 230);
        renderer.line(minX, minY, maxZ, minX, maxY, maxZ, r, g, b, 230);

        // Current mining target box
        if (currentTarget != null) {
            double cx = currentTarget.getX();
            double cy = currentTarget.getY();
            double cz = currentTarget.getZ();
            renderer.quad(cx, cy, cz, cx + 1, cy, cz, cx + 1, cy, cz + 1, cx, cy, cz + 1, 0, 255, 0, 60);
            renderer.quad(cx, cy + 1, cz, cx, cy + 1, cz + 1, cx + 1, cy + 1, cz + 1, cx + 1, cy + 1, cz, 0, 255, 0, 60);
            renderer.quad(cx, cy, cz + 1, cx + 1, cy, cz + 1, cx + 1, cy + 1, cz + 1, cx, cy + 1, cz + 1, 0, 255, 0, 60);
            renderer.quad(cx, cy, cz, cx, cy + 1, cz, cx + 1, cy + 1, cz, cx + 1, cy, cz, 0, 255, 0, 60);
            renderer.quad(cx + 1, cy, cz, cx + 1, cy + 1, cz, cx + 1, cy + 1, cz + 1, cx + 1, cy, cz + 1, 0, 255, 0, 60);
            renderer.quad(cx, cy, cz, cx, cy, cz + 1, cx, cy + 1, cz + 1, cx, cy + 1, cz, 0, 255, 0, 60);
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public HudPhase getHudPhase() {
        return HudPhase.LAST;
    }
}
