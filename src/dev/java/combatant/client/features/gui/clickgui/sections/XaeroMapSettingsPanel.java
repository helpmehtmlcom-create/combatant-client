/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.sections;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ModeValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.StringValue;
import combatant.client.config.values.SetValue;
import combatant.client.config.subsystem.MapUiConfig;
import combatant.client.config.subsystem.MapHeuristicConfig;
import combatant.client.config.subsystem.MapLinkConfig;
import combatant.client.config.subsystem.MapTriangulationConfig;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.implement.other.SearchComponent;
import combatant.client.features.gui.clickgui.settings.BooleanSetting;
import combatant.client.features.gui.clickgui.settings.ColorSetting;
import combatant.client.features.gui.clickgui.settings.ModeSetting;
import combatant.client.features.gui.clickgui.settings.Setting;
import combatant.client.features.gui.clickgui.settings.SettingFactory;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.gui.clickgui.settings.SettingRenderContext;
import combatant.client.features.gui.clickgui.settings.SettingRenderSurface;
import combatant.client.features.gui.clickgui.settings.SliderSetting;
import combatant.client.features.gui.clickgui.settings.TextSetting;
import combatant.client.features.gui.hud.script.HudScriptLayouts;
import combatant.client.features.map.location.PlayerLocationService;
import combatant.client.features.map.location.PlayerLocationSnapshot;
import combatant.client.features.map.location.PlayerLocationSource;
import combatant.client.features.map.heuristic.HeuristicEstimate;
import combatant.client.features.map.heuristic.HeuristicRuntime;
import combatant.client.features.map.heuristic.HeuristicRuntimeStats;
import combatant.client.features.map.heuristic.MapTriangulationMode;
import combatant.client.features.maplink.model.MapLinkProfile;
import combatant.client.features.maplink.model.MapLinkProviderType;
import combatant.client.features.maplink.model.MapLinkProfileState;
import combatant.client.features.maplink.model.MapLinkProfileStatus;
import combatant.client.features.maplink.model.MapLinkSnapshot;
import combatant.client.features.maplink.runtime.MapLinkRuntime;
import combatant.client.features.maplink.runtime.MapLinkServerMatcher;
import combatant.client.features.theme.Theme;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiBackdropRequest;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.render.UiProjectionMode;
import combatant.client.render.engine.renderer.ui.runtime.render.UiRenderContext;
import combatant.client.render.engine.renderer.ui.runtime.script.CachedUiScriptRuntime;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModule;
import combatant.client.render.engine.renderer.ui.runtime.script.UiScriptModuleHandle;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.util.logging.DebugLog;
import combatant.client.util.resources.asset.UiScriptAsset;
import combatant.client.util.text.LegacyTextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import xaero.lib.client.config.ClientConfigManager;
import xaero.lib.common.config.Config;
import xaero.lib.common.config.option.BooleanConfigOption;
import xaero.lib.common.config.option.ConfigOption;
import xaero.lib.common.config.option.IndexedConfigOption;
import xaero.lib.common.config.option.RangeConfigOption;
import xaero.lib.common.config.option.SteppedConfigOption;
import xaero.map.MapProcessor;
import xaero.map.WorldMap;
import xaero.map.common.config.option.WorldMapProfiledConfigOptions;
import xaero.map.config.primary.option.WorldMapPrimaryClientConfigOptions;
import xaero.map.config.util.WorldMapClientConfigUtils;
import xaero.map.world.MapDimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

@UiScriptAsset("combatant:modules/clickgui/map_settings")
final class XaeroMapSettingsPanel {
    private static final float DESIGN_WIDTH = 1120.0f;
    private static final float DESIGN_HEIGHT = 720.0f;
    private static final float MIN_WIDTH = 820.0f;
    private static final float MIN_HEIGHT = 540.0f;
    private static final float SCREEN_INSET = 24.0f;
    private static final float BASE_INSET = 16.0f;
    private static final float HEADER_HEIGHT = 62.0f;
    private static final float SEARCH_WIDTH = 210.0f;
    private static final float SEARCH_HEIGHT = 34.0f;
    private static final float CLOSE_SIZE = 32.0f;

    private final UiScriptModuleHandle moduleHandle = HudScriptLayouts.handle(XaeroMapSettingsPanel.class);
    private final CachedUiScriptRuntime scriptRuntime = new CachedUiScriptRuntime(HudScriptLayouts.runtimeReporter());
    private final Supplier<MapProcessor> processorSupplier;
    private final Supplier<MapDimension> dimensionSupplier;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final Set<ConfigOption<?>> boundXaeroOptions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final SearchComponent searchComponent = new SearchComponent();

    private Category selectedCategory = Category.DISPLAY;
    private boolean open;
    private float openAnim;
    private float contentAnim = 1.0f;
    private boolean searchFocused;
    private String search = "";
    private float x;
    private float y;
    private float width;
    private float height;
    private float scroll;
    private float maxScroll;
    private float searchX;
    private float searchY;
    private float searchW;
    private float searchH;
    private float closeX;
    private float closeY;
    private float closeW;
    private float closeH;
    private boolean profiledSavePending;
    private String selectedMapLinkProfileId = "";
    private boolean primarySavePending;
    private long saveDeadlineNs;
    private final SearchComponent.Model searchModel = new SearchComponent.Model() {
        @Override public boolean focused() { return searchFocused; }
        @Override public String text() { return search; }
        @Override public String placeholder() { return tr("gui.combatant.map.browser.search", "Search"); }
        @Override public void setFocused(boolean focused) { searchFocused = focused; }
    };

    XaeroMapSettingsPanel(Supplier<MapProcessor> processorSupplier, Supplier<MapDimension> dimensionSupplier) {
        this.processorSupplier = processorSupplier != null ? processorSupplier : () -> null;
        this.dimensionSupplier = dimensionSupplier != null ? dimensionSupplier : () -> null;

        ClientConfigManager manager = WorldMap.INSTANCE.getConfigs().getClientConfigManager();
        Config primary = manager.getPrimaryConfigManager().getConfig();

        addCombatantArrowSettings();

        addProfiled(manager, WorldMapProfiledConfigOptions.COORDINATES, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.FOOTSTEPS, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.ARROW, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.ARROW_COLOR, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_ZOOM, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_HOVERED_BIOME, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.ZOOM_BUTTONS, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPENING_ANIMATION, Category.DISPLAY);
        addProfiled(manager, WorldMapProfiledConfigOptions.MAP_ITEM, Category.DISPLAY);

        addProfiled(manager, WorldMapProfiledConfigOptions.LIGHTING, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.BLOCK_COLORS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.LOAD_NEW_CHUNKS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.UPDATE_CHUNKS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.TERRAIN_DEPTH, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.TERRAIN_SLOPES, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.BIOME_BLENDING, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.BIOME_COLORS_IN_VANILLA, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.ADJUST_HEIGHT_FOR_SHORT_BLOCKS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.FLOWERS, Category.TERRAIN);
        addProfiled(manager, WorldMapProfiledConfigOptions.STAINED_GLASS, Category.TERRAIN);

        addProfiled(manager, WorldMapProfiledConfigOptions.WAYPOINTS, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.RENDER_WAYPOINTS, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.WAYPOINT_BACKGROUNDS, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.WAYPOINT_SCALE, Category.WAYPOINTS);
        addProfiled(manager, WorldMapProfiledConfigOptions.MIN_ZOOM_LOCAL_WAYPOINTS, Category.WAYPOINTS);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DISPLAY_DISABLED_WAYPOINTS, Category.WAYPOINTS);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.CLOSE_WAYPOINTS_AFTER_HOP, Category.WAYPOINTS);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.ONLY_CURRENT_MAP_WAYPOINTS, Category.WAYPOINTS);

        addProfiled(manager, WorldMapProfiledConfigOptions.CAVE_MODE_ALLOWED, Category.CAVE);
        addProfiledSet(manager, WorldMapProfiledConfigOptions.CAVE_MODE_ALLOWED_DIMENSIONS, Category.CAVE);
        addCurrentCaveMode();
        addProfiled(manager, WorldMapProfiledConfigOptions.CAVE_MODE_DEPTH, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.LEGIBLE_CAVE_MAPS, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.AUTO_CAVE_MODE, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.CAVE_MODE_TOGGLE_TIMER, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_CAVE_MODE_TYPE, Category.CAVE);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_CAVE_MODE_START, Category.CAVE);
        addCaveStart(primary);

        addProfiled(manager, WorldMapProfiledConfigOptions.MINIMAP_RADAR, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.DISPLAY_TRACKED_PLAYERS, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPAC_CLAIMS, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPAC_CLAIMS_BORDER_OPACITY, Category.PLAYERS);
        addProfiled(manager, WorldMapProfiledConfigOptions.OPAC_CLAIMS_FILL_OPACITY, Category.PLAYERS);

        addProfiled(manager, WorldMapProfiledConfigOptions.MAP_TELEPORT_ALLOWED, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.PARTIAL_Y_TELEPORT, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_MAP_TELEPORT_FORMAT, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_MAP_TELEPORT_DIMENSION_FORMAT, Category.NAVIGATION);
        addProfiled(manager, WorldMapProfiledConfigOptions.DEFAULT_PLAYER_TELEPORT_FORMAT, Category.NAVIGATION);

        addPrimary(primary, WorldMapPrimaryClientConfigOptions.EXPORT_MULTIPLE_IMAGES, Category.EXPORT);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.NIGHT_EXPORT, Category.EXPORT);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.EXPORT_SCALE_DOWN_SQUARE, Category.EXPORT);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.EXPORT_HIGHLIGHTS, Category.EXPORT);

        addProfiled(manager, WorldMapProfiledConfigOptions.WRITING_DISTANCE, Category.ADVANCED);
        addProfiled(manager, WorldMapProfiledConfigOptions.DETECT_AMBIGUOUS_Y, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.MAX_LOADED_REGIONS, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.UPDATE_NOTIFICATIONS, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DEBUG, Category.ADVANCED);
        addPrimary(primary, WorldMapPrimaryClientConfigOptions.DIFFERENTIATE_BY_SERVER_ADDRESS, Category.ADVANCED);

        addTargetingFrontend();
        addTriangulationFrontend();
        addMapLinkFrontend();
        auditXaeroCoverage();
    }

    boolean isOpen() {
        return open;
    }

    boolean isVisible() {
        return open || openAnim > 0.002f;
    }

    void toggle() {
        open = !open;
        if (open) contentAnim = 0.0f;
        else searchFocused = false;
    }

    void openCave() {
        selectedCategory = Category.CAVE;
        scroll = 0.0f;
        open = true;
        contentAnim = 0.0f;
        searchFocused = false;
    }

    void close() {
        open = false;
        searchFocused = false;
        flushXaeroSaves();
    }

    void render(float viewportX, float viewportY, float viewportWidth, float viewportHeight,
                float mouseX, float mouseY) {
        float dt = AnimationUtility.deltaTime();
        float target = open ? 1.0f : 0.0f;
        openAnim = AnimationUtility.approach(openAnim, target, dt, open ? 10.5f : 12.0f);
        openAnim = AnimationUtility.snap(openAnim, target, 0.002f);
        contentAnim = AnimationUtility.approach(contentAnim, 1.0f, dt, 8.5f);
        contentAnim = AnimationUtility.snap(contentAnim, 1.0f, 0.002f);
        flushXaeroSavesIfDue();
        if (!open && openAnim <= 0.001f) {
            openAnim = 0.0f;
            hits.clear();
            categoryHits.clear();
            return;
        }

        width = Math.min(DESIGN_WIDTH, Math.max(MIN_WIDTH, viewportWidth - SCREEN_INSET));
        height = Math.min(DESIGN_HEIGHT, Math.max(MIN_HEIGHT, viewportHeight - SCREEN_INSET));
        float eased = easeOutCubic(openAnim);
        x = viewportX + (viewportWidth - width) * 0.5f;
        y = viewportY + (viewportHeight - height) * 0.5f + (1.0f - eased) * 16.0f;

        SolidBrowserLayout layout = SolidBrowserLayout.of(width, height);
        updateInteractiveGeometry(layout);

        float lifecycleAlpha = smootherStep(openAnim);

        // The scripted Browser owns its lifecycle alpha through UiRenderContext so text, images,
        // primitives and glass all decay together. Do not multiply Renderer2D alpha around it as
        // well, otherwise the optical surface fades twice while runtime text only fades once.
        renderBrowserSurface(layout, lifecycleAlpha);

        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * lifecycleAlpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * lifecycleAlpha);
        try {
            searchComponent.render(searchX, searchY, searchW, searchH, searchModel);
            renderSettingsContent(layout, mouseX, mouseY, contentAnim);
        } finally {
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
        }
    }

    private void renderBrowserSurface(SolidBrowserLayout layout, float lifecycleAlpha) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getResourceManager() == null) return;
        HudScriptLayouts.pollReloadCombo(mc);
        if (moduleHandle.consumeChanged()) scriptRuntime.reset();
        if (!moduleHandle.ensureLoaded(mc.getResourceManager())) {
            HudScriptLayouts.reportLoadError(moduleHandle);
            return;
        }
        UiScriptModule module = moduleHandle.module();
        if (module == null) return;

        LinkedHashMap<String, Object> props = new LinkedHashMap<>();
        props.put("width", width);
        props.put("height", height);
        props.put("title", tr("gui.combatant.map.browser.title", "World Map"));
        props.put("selectedCategory", selectedCategory.id);
        props.put("accent", hex(Theme.theme().accent()));
        props.put("layout", layout.toProps());
        List<LinkedHashMap<String, Object>> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("id", category.id);
            item.put("label", category.label());
            item.put("description", category.description());
            item.put("icon", category.icon);
            categories.add(item);
        }
        props.put("categories", categories);

        long signature = CachedUiScriptRuntime.signature(props);
        UiRuntime runtime = scriptRuntime.bake(
                moduleHandle,
                module,
                "map-settings",
                signature,
                signature,
                signature,
                width,
                height,
                ClickGuiRenderer.getOnestMedium(),
                x,
                y,
                width,
                height,
                () -> props,
                null
        );
        if (runtime != null) {
            // Use the same dedicated Map UI-underlay source as the chrome controls. The map
            // background and both ordinary/direct-textured Xaero tile paths are explicitly mirrored
            // into this target before any Map glass is emitted, so it is the authoritative optical
            // source for UI-native glass. CURRENT_TARGET is intentionally not used here: during the
            // deferred/clip pipeline the visible Map can still live in an intermediate attachment at
            // the point the Browser batch is replayed, which made the snapshot resolve as black.
            Renderer2D.COLOR.withLiquidGlassSceneSource(
                    UiBackdropRequest.SceneSource.UI_UNDERLAY,
                    () -> runtime.render(new UiRenderContext(
                            Renderer2D.COLOR,
                            ClickGuiRenderer.getOnestMedium(),
                            null,
                            0.0f,
                            UiProjectionMode.CURRENT,
                            lifecycleAlpha
                    ))
            );
        }
    }

    private void renderSettingsContent(SolidBrowserLayout layout, float mouseX, float mouseY, float transition) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        hits.clear();

        float contentEase = easeOutCubic(transition);
        float contentAlpha = 0.62f + 0.38f * smootherStep(transition);
        float contentX = x + layout.detailX + layout.contentX;
        float contentY = y + layout.contentY + (1.0f - contentEase) * 8.0f;
        float contentW = layout.contentWidth;
        float contentH = layout.contentHeight;
        float cursorY = contentY + scroll;
        float total = 0.0f;
        float columnGap = 12.0f;
        float columnWidth = Math.max(1.0f, (contentW - columnGap) * 0.5f);
        float rowGap = 10.0f;

        boolean clipped = ScissorFunction.pushRaw(contentX, contentY, contentW, contentH);
        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * contentAlpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * contentAlpha);
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            List<Entry> visible = entries.stream().filter(this::matches).filter(this::participatesInLayout).toList();
            for (int index = 0; index < visible.size();) {
                Entry left = visible.get(index);
                float leftVis = clamp(left.setting.getVisibilityAnim(), 0.0f, 1.0f);
                Entry right = isCompact(left.setting)
                        && index + 1 < visible.size()
                        && isCompact(visible.get(index + 1).setting)
                        ? visible.get(index + 1)
                        : null;
                float rightVis = right == null ? 0.0f : clamp(right.setting.getVisibilityAnim(), 0.0f, 1.0f);
                float leftBaseHeight = left.setting.getHeightSafely();
                float rightBaseHeight = right == null ? 0.0f : right.setting.getHeightSafely();
                float leftHeight = leftBaseHeight * leftVis;
                float rightHeight = rightBaseHeight * rightVis;
                float rowHeight = Math.max(leftHeight, rightHeight);

                if (rowHeight > 0.2f && cursorY + rowHeight >= contentY && cursorY <= contentY + contentH) {
                    float leftWidth = right == null ? preferredWidth(left.setting, contentW) : columnWidth;
                    renderAnimatedSetting(left.setting, contentX, cursorY, leftWidth, leftBaseHeight, leftHeight, leftVis, mouseX, mouseY);
                    if (left.setting.isVisibilityTargetVisibleSafely() && leftVis > 0.45f) {
                        hits.add(new Hit(left.setting, contentX, cursorY, leftWidth, leftHeight));
                    }
                    if (right != null) {
                        float rightX = contentX + columnWidth + columnGap;
                        renderAnimatedSetting(right.setting, rightX, cursorY, columnWidth, rightBaseHeight, rightHeight, rightVis, mouseX, mouseY);
                        if (right.setting.isVisibilityTargetVisibleSafely() && rightVis > 0.45f) {
                            hits.add(new Hit(right.setting, rightX, cursorY, columnWidth, rightHeight));
                        }
                    }
                }
                cursorY += rowHeight + (rowHeight > 0.2f ? rowGap : 0.0f);
                total += rowHeight + (rowHeight > 0.2f ? rowGap : 0.0f);
                index += right == null ? 1 : 2;
            }
        } finally {
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
            if (clipped) ScissorFunction.pop();
        }

        if (total <= 0.0f) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.browser.no_results", "No matching settings"),
                    contentX + 6.0f, contentY + 16.0f, 18.0f,
                    SettingsGuiPalette.withAlpha(palette.panelMuted(), Math.round(255.0f * contentAlpha)), false);
        }
        maxScroll = Math.max(0.0f, total - contentH);
        scroll = clamp(scroll, -maxScroll, 0.0f);
    }

    private void renderAnimatedSetting(Setting setting, float sx, float sy, float sw,
                                       float baseHeight, float animatedHeight, float visibility,
                                       float mouseX, float mouseY) {
        if (visibility <= 0.001f || animatedHeight <= 0.001f) return;
        float alpha = smootherStep(visibility);
        float offsetY = (1.0f - easeOutCubic(visibility)) * 7.0f;
        double previousRendererAlpha = Renderer2D.COLOR.getAlpha();
        float previousGuiAlpha = ClickGuiRenderer.getRenderAlphaMultiplier();
        Renderer2D.COLOR.setAlpha(previousRendererAlpha * alpha);
        ClickGuiRenderer.setRenderAlphaMultiplier(previousGuiAlpha * alpha);
        boolean clip = ScissorFunction.pushRaw(sx, sy, sw, Math.max(0.5f, animatedHeight));
        try {
            setting.renderSafely(sx, sy + offsetY, sw, mouseX, mouseY);
        } finally {
            if (clip) ScissorFunction.pop();
            ClickGuiRenderer.restoreRenderAlphaMultiplier(previousGuiAlpha);
            Renderer2D.COLOR.setAlpha(previousRendererAlpha);
        }
    }

    private void updateInteractiveGeometry(SolidBrowserLayout layout) {
        categoryHits.clear();
        float navX = x + layout.navX;
        float navY = y + layout.navStartY;
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            categoryHits.add(new CategoryHit(categories[i], navX,
                    navY + i * (layout.navRowHeight + layout.navRowGap),
                    layout.navRowWidth, layout.navRowHeight));
        }
        searchX = x + layout.detailX + layout.toolbarLeftX;
        searchY = y + layout.toolbarY;
        searchW = layout.searchWidth;
        searchH = layout.searchHeight;
        closeX = x + layout.detailX + layout.closeX;
        closeY = y + layout.closeY;
        closeW = layout.closeWidth;
        closeH = layout.closeHeight;
    }

    boolean mousePressed(float mouseX, float mouseY, int button) {
        if (!open) return isVisible();
        if (!inside(mouseX, mouseY, x, y, width, height)) {
            close();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && inside(mouseX, mouseY, closeX, closeY, closeW, closeH)) {
            close();
            return true;
        }
        if (searchComponent.click(searchX, searchY, searchW, searchH, mouseX, mouseY, button, searchModel)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (CategoryHit hit : categoryHits) {
                if (!hit.contains(mouseX, mouseY)) continue;
                if (selectedCategory != hit.category) {
                    selectedCategory = hit.category;
                    contentAnim = 0.0f;
                }
                scroll = 0.0f;
                return true;
            }
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)) {
                    hit.setting.mouseClickedSafely(mouseX, mouseY, button, hit.x, hit.y, hit.width);
                } else {
                    hit.setting.mouseClickedOutsideSafely(mouseX, mouseY, button);
                }
            }
        }
        return true;
    }

    void mouseReleased(float mouseX, float mouseY, int button) {
        if (!open) return;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Entry entry : entries) entry.setting.mouseReleasedSafely(mouseX, mouseY, button);
        }
    }

    boolean mouseScrolled(float mouseX, float mouseY, double amount) {
        if (!isVisible()) return false;
        if (!open || !inside(mouseX, mouseY, x, y, width, height)) return true;
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.width, hit.height)
                        && hit.setting.mouseScrolledSafely(mouseX, mouseY, amount)) return true;
            }
        }
        scroll = clamp(scroll + (float) amount * 44.0f, -maxScroll, 0.0f);
        return true;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!search.isEmpty()) {
                    search = "";
                    contentAnim = 0.0f;
                } else searchFocused = false;
                scroll = 0.0f;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
                search = search.substring(0, search.length() - 1);
                contentAnim = 0.0f;
                scroll = 0.0f;
                return true;
            }
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Entry entry : entries) {
                if (matches(entry) && entry.setting.keyPressedSafely(keyCode, scanCode, modifiers)) return true;
            }
        }
        return searchFocused;
    }

    boolean charTyped(char chr, int modifiers) {
        if (!open) return false;
        if (searchFocused && !Character.isISOControl(chr) && search.length() < 64) {
            search += chr;
            contentAnim = 0.0f;
            scroll = 0.0f;
            return true;
        }
        try (SettingRenderContext.Scope ignored = SettingRenderContext.push(SettingRenderSurface.SETTINGS, 1.12f)) {
            for (Entry entry : entries) {
                if (matches(entry) && entry.setting.charTypedSafely(chr, modifiers)) return true;
            }
        }
        return searchFocused;
    }

    private boolean matches(Entry entry) {
        if (search.isBlank()) return entry.category == selectedCategory;
        String needle = search.toLowerCase(Locale.ROOT).trim();
        if (entry.searchText.contains(needle)) return true;
        return entry.setting instanceof DynamicSearchEntry dynamic && dynamic.dynamicSearchText().contains(needle);
    }

    private boolean participatesInLayout(Entry entry) {
        float visibility = entry.setting.updateVisibilitySafely();
        return entry.setting.isVisibilityTargetVisibleSafely() || visibility > 0.012f;
    }

    private static boolean isCompact(Setting setting) {
        return setting instanceof BooleanSetting || setting instanceof ModeSetting || setting instanceof SliderSetting<?>;
    }

    private static float preferredWidth(Setting setting, float available) {
        if (setting instanceof ColorSetting) return Math.min(available, 340.0f);
        if (setting instanceof SliderSetting<?>) return Math.min(available, 420.0f);
        if (setting instanceof TextSetting || setting instanceof TextListSetting) return Math.min(available, 520.0f);
        return available;
    }

    private void addCombatantArrowSettings() {
        MapUiConfig config = MapUiConfig.get();
        ModeSetting mode = new ModeSetting(tr("gui.combatant.map.display.arrow_color", "Player arrow color"), config.arrowColorModeValue());
        mode.setI18nEnabled(false, false);
        entries.add(new Entry(mode, Category.DISPLAY, "player arrow color theme custom"));

        ColorSetting color = new ColorSetting(tr("gui.combatant.map.display.arrow_custom", "Custom arrow color"), config.arrowCustomColorValue());
        color.setI18nEnabled(false, false);
        color.visibleWhen(config::isCustomArrowColor);
        entries.add(new Entry(color, Category.DISPLAY, "custom player arrow color tint"));
    }

    private void addCurrentCaveMode() {
        List<Integer> modes = List.of(0, 1, 2);
        List<String> labels = List.of(
                tr("gui.xaero_off", "Off"),
                tr("gui.xaero_wm_cave_mode_type_layered", "Layered"),
                tr("gui.xaero_wm_cave_mode_type_full", "Full")
        );
        ModeSetting setting = new ModeSetting(tr("gui.combatant.map.cave.current_mode", "Current cave mode"), new ExternalModeValue<>(
                "currentCaveMode",
                () -> {
                    MapDimension dimension = dimensionSupplier.get();
                    return dimension == null ? 0 : Math.floorMod(dimension.getCaveModeType(), 3);
                },
                this::setCaveMode,
                modes,
                labels
        ));
        setting.setI18nEnabled(false, false);
        entries.add(new Entry(setting, Category.CAVE, "current cave mode off layered full"));
    }

    private void setCaveMode(int target) {
        MapDimension dimension = dimensionSupplier.get();
        if (dimension == null || !WorldMapClientConfigUtils.getEffectiveCaveModeAllowed()) return;
        target = Math.floorMod(target, 3);
        for (int i = 0; i < 3 && Math.floorMod(dimension.getCaveModeType(), 3) != target; i++) {
            dimension.toggleCaveModeType(true);
        }
        MapProcessor processor = processorSupplier.get();
        if (processor != null) {
            synchronized (processor.uiSync) {
                dimension.saveConfigUnsynced();
            }
            processor.updateCaveStart();
        }
    }

    private void addCaveStart(Config primary) {
        ConfigOption<Integer> option = WorldMapPrimaryClientConfigOptions.CAVE_MODE_START;
        String name = resolveDisplayName(option);
        SliderSetting<Integer> setting = new SliderSetting<>(name, new ExternalCaveStartValue(
                option.getId(),
                () -> {
                    Integer value = primary.get(option);
                    return value == null || value == Integer.MAX_VALUE ? 320 : Math.max(-64, Math.min(319, value));
                },
                value -> {
                    int clamped = Math.max(-64, Math.min(320, value));
                    primary.set(option, clamped >= 320 ? Integer.MAX_VALUE : clamped);
                    markPrimaryDirty();
                    MapProcessor processor = processorSupplier.get();
                    if (processor != null) processor.updateCaveStart();
                }));
        setting.setI18nEnabled(false, false);
        entries.add(new Entry(setting, Category.CAVE, searchText(option, name) + " auto y layer height"));
        boundXaeroOptions.add(option);
    }

    private void addProfiled(ClientConfigManager manager, ConfigOption<?> option, Category category) {
        if (add(option, category,
                () -> profiledDisplayValue(manager, raw(option)),
                value -> {
                    manager.getCurrentProfile().set(raw(option), value);
                    markProfiledDirty();
                },
                () -> profiledUnavailableReason(manager, option))) {
            boundXaeroOptions.add(option);
        }
    }

    private void addProfiledSet(ClientConfigManager manager, ConfigOption<Set<Identifier>> option, Category category) {
        if (option == null) return;
        String name = resolveDisplayName(option);
        ExternalIdentifierSetValue value = new ExternalIdentifierSetValue(option.getId(),
                () -> profiledDisplayValue(manager, option),
                next -> {
                    manager.getCurrentProfile().set(option, next);
                    markProfiledDirty();
                });
        TextListSetting setting = new TextListSetting(name, value, TextListSetting.PickerMode.TEXT);
        setting.setI18nEnabled(false, false);
        setting.unavailableReason(() -> profiledUnavailableReason(manager, option));
        entries.add(new Entry(setting, category, searchText(option, name) + " dimensions worlds"));
        boundXaeroOptions.add(option);
    }

    private void addPrimary(Config config, ConfigOption<?> option, Category category) {
        if (add(option, category, () -> config.get(raw(option)), value -> {
            config.set(raw(option), value);
            markPrimaryDirty();
        }, null)) {
            boundXaeroOptions.add(option);
        }
    }

    private <T> boolean add(ConfigOption<T> option, Category category, Supplier<T> getter, Consumer<T> setter, Supplier<String> unavailableReason) {
        if (option == null) return false;
        try {
            Setting setting;
            String name = resolveDisplayName(option);
            if (option instanceof BooleanConfigOption) {
                setting = new BooleanSetting(name, new ExternalBooleanValue(option.getId(),
                        () -> (Boolean) getter.get(), value -> setter.accept(cast(value))));
            } else if (isExplicitModeOption(option) && option instanceof IndexedConfigOption<?> indexed) {
                setting = modeSetting(name, option, indexed, getter, setter);
            } else if (option instanceof RangeConfigOption && option instanceof IndexedConfigOption<?> indexed
                    && !indexed.getValidValues().isEmpty()) {
                List<Integer> values = indexed.getValidValues().stream().map(value -> (Integer) value).toList();
                setting = new SliderSetting<>(name, new ExternalIntegerValue(option.getId(),
                        () -> (Integer) getter.get(), value -> setter.accept(cast(value)), values,
                        raw(option)));
            } else if (option instanceof SteppedConfigOption && option instanceof IndexedConfigOption<?> indexed
                    && !indexed.getValidValues().isEmpty()) {
                List<Double> values = indexed.getValidValues().stream().map(value -> (Double) value).toList();
                setting = new SliderSetting<>(name, new ExternalDoubleValue(option.getId(),
                        () -> (Double) getter.get(), value -> setter.accept(cast(value)), values,
                        raw(option)));
            } else if (option instanceof IndexedConfigOption<?> indexed && !indexed.getValidValues().isEmpty()) {
                setting = modeSetting(name, option, indexed, getter, setter);
            } else if (option.getDefaultValue() instanceof String) {
                setting = new TextSetting(name, new ExternalStringValue(option.getId(),
                        () -> (String) getter.get(), value -> setter.accept(cast(value))));
            } else {
                return false;
            }
            setting.setI18nEnabled(false, false);
            if (unavailableReason != null) setting.unavailableReason(unavailableReason);
            entries.add(new Entry(setting, category, searchText(option, name)));
            return true;
        } catch (RuntimeException | LinkageError error) {
            String id = option.getId() == null ? "unknown" : option.getId();
            DebugLog.warnOnce("clickgui-map-setting-" + id,
                    "Skipping unavailable Xaero World Map setting: " + id, error);
            return false;
        }
    }

    private void markProfiledDirty() {
        profiledSavePending = true;
        saveDeadlineNs = System.nanoTime() + 250_000_000L;
    }

    private static <T> T profiledDisplayValue(ClientConfigManager manager, ConfigOption<T> option) {
        if (manager == null || option == null) return null;
        try {
            if (isProfiledControlled(manager, option)) return manager.getEffective(option);
        } catch (RuntimeException | LinkageError error) {
            DebugLog.warnOnce("clickgui-map-xaero-effective-" + option.getId(),
                    "Failed to resolve effective Xaero value for " + option.getId(), error);
        }
        return manager.getRaw(option);
    }

    private static boolean isProfiledControlled(ClientConfigManager manager, ConfigOption<?> option) {
        if (manager.getRedirectorManager().shouldDeactivateWidget(option)) return true;
        return !manager.shouldIgnoreServerEnforcement(raw(option))
                && manager.getServerSynced().getEffective(raw(option)) != null;
    }

    private static String profiledUnavailableReason(ClientConfigManager manager, ConfigOption<?> option) {
        if (manager == null || option == null) return null;
        try {
            if (manager.getRedirectorManager().shouldDeactivateWidget(option)) {
                var tooltip = manager.getRedirectorManager().getTooltip(option);
                String detail = tooltip == null ? "" : LegacyTextUtil.stripLegacy(tooltip.getString()).trim();
                return detail.isBlank() ? "Controlled by Xaero compatibility override" : detail;
            }
            if (isProfiledControlled(manager, option)) return "Enforced by server";
        } catch (RuntimeException | LinkageError error) {
            DebugLog.warnOnce("clickgui-map-xaero-override-" + option.getId(),
                    "Failed to resolve Xaero override state for " + option.getId(), error);
        }
        return null;
    }

    private void markPrimaryDirty() {
        primarySavePending = true;
        saveDeadlineNs = System.nanoTime() + 250_000_000L;
    }

    private void flushXaeroSavesIfDue() {
        if ((!profiledSavePending && !primarySavePending) || System.nanoTime() < saveDeadlineNs) return;
        flushXaeroSaves();
    }

    private void flushXaeroSaves() {
        try {
            var channel = WorldMap.INSTANCE.getConfigs();
            if (profiledSavePending) {
                channel.getClientConfigProfileIO().save(channel.getClientConfigManager().getCurrentProfile());
                profiledSavePending = false;
            }
            if (primarySavePending) {
                channel.getPrimaryClientConfigManagerIO().save();
                primarySavePending = false;
            }
        } catch (RuntimeException | LinkageError error) {
            DebugLog.warnOnce("clickgui-map-xaero-save", "Failed to persist Xaero map config", error);
        }
    }

    private void addSubsystemSettings(combatant.client.config.subsystem.SubsystemConfig config, Category category, String... skipIds) {
        Set<String> skip = skipIds == null ? Set.of() : Set.of(skipIds);
        for (var def : config.getSettingDefs()) {
            if (def == null || skip.contains(def.getId())) continue;
            Setting setting = SettingFactory.fromDef(def);
            if (setting == null) continue;
            setting.setParent(config);
            entries.add(new Entry(setting, category, (def.getId() + " " + setting.getName()).toLowerCase(Locale.ROOT)));
        }
    }

    private void addTargetingFrontend() {
        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.targets.section.live", "Live targets"),
                tr("gui.combatant.map.targets.section.live.description", "Pick players from the current server or from any active location source.")),
                Category.TARGETS, "live targets players location source"));
        TargetPlayersSetting targets = new TargetPlayersSetting();
        targets.setI18nEnabled(false, false);
        entries.add(new Entry(targets, Category.TARGETS, "target players targeted locator triangulation tracking player list"));
    }

    private void addTriangulationFrontend() {
        entries.add(new Entry(new TriangulationModeSetting(), Category.TRIANGULATION,
                "triangulation mode off data mining targeted collection workload"));

        TriangulationStatusSetting status = new TriangulationStatusSetting();
        status.setI18nEnabled(false, false);
        entries.add(new Entry(status, Category.TRIANGULATION,
                "triangulation solver status observations accepted rejected confidence uncertainty estimates data mining targeted"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.triangulation.section.workload", "Collection workload"),
                tr("gui.combatant.map.triangulation.section.workload.description", "How often observations are solved and how many players Data Mining may process.")),
                Category.TRIANGULATION, "triangulation workload solve interval targets"));
        addSubsystemSetting(MapTriangulationConfig.get(), Category.TRIANGULATION, "dataMiningMinSolveIntervalMs");
        addSubsystemSetting(MapTriangulationConfig.get(), Category.TRIANGULATION, "targetedMinSolveIntervalMs");
        addSubsystemSetting(MapTriangulationConfig.get(), Category.TRIANGULATION, "dataMiningMaxActiveTargets");

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.triangulation.section.quality", "Solver quality"),
                tr("gui.combatant.map.triangulation.section.quality.description", "Observation retention, required movement and bearing separation.")),
                Category.TRIANGULATION, "solver quality samples baseline bearing noise"));
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "enabled");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "maxSamplesPerTarget");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "maxSampleAgeMs");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "minBaseline");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "minBearingDeltaDegrees");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "bearingNoiseDegrees");

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.triangulation.section.reset", "Movement & rejection"),
                tr("gui.combatant.map.triangulation.section.reset.description", "Advanced gates used to reject backward rays and start a new estimate segment when the target moves.")),
                Category.TRIANGULATION, "movement reset reject segment advanced"));
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "forwardRejectTolerance");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "segmentResetDistance");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "segmentResetSigma");
        addSubsystemSetting(MapHeuristicConfig.get(), Category.TRIANGULATION, "segmentResetMinConfidence");
    }

    private void addMapLinkFrontend() {
        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.maplink.section.profiles", "Server profiles"),
                tr("gui.combatant.map.maplink.section.profiles.description", "Each profile binds one web-map provider to a Minecraft server. Create one for the server you are currently connected to, then configure its map URL.")),
                Category.MAPLINK, "maplink profiles server create current server"));

        MapLinkStatusSetting status = new MapLinkStatusSetting();
        status.setI18nEnabled(false, false);
        entries.add(new Entry(status, Category.MAPLINK, "maplink providers profiles status live stale players worlds server"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.maplink.section.selected", "Selected profile"),
                tr("gui.combatant.map.maplink.section.selected.description", "Provider, server matcher and map endpoint for the selected server profile."),
                this::hasSelectedMapLinkProfile), Category.MAPLINK, "maplink selected profile editor"));

        TextSetting displayName = new TextSetting(tr("gui.combatant.map.maplink.profile.name", "Profile name"),
                new ExternalStringValue("mapLinkProfileName", this::selectedMapLinkDisplayName,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, value, profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()))));
        displayName.setI18nEnabled(false, false);
        displayName.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(displayName, Category.MAPLINK, "maplink profile name label"));

        TextSetting serverMatcher = new TextSetting(tr("gui.combatant.map.maplink.profile.server", "Minecraft server"),
                new ExternalStringValue("mapLinkServerMatcher", this::selectedMapLinkServerMatcher,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), value, profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()))));
        serverMatcher.setI18nEnabled(false, false);
        serverMatcher.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(serverMatcher, Category.MAPLINK, "maplink profile server matcher address host ip"));

        TextSetting baseUrl = new TextSetting(tr("gui.combatant.map.maplink.profile.url", "Web map URL"),
                new ExternalStringValue("mapLinkBaseUrl", this::selectedMapLinkBaseUrl,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), value, profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()))));
        baseUrl.setI18nEnabled(false, false);
        baseUrl.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(baseUrl, Category.MAPLINK, "maplink profile url base endpoint web map"));

        List<MapLinkProviderType> providers = List.of(MapLinkProviderType.values());
        List<String> providerLabels = providers.stream().map(XaeroMapSettingsPanel::mapLinkProviderLabel).toList();
        ModeSetting provider = new ModeSetting(tr("gui.combatant.map.maplink.profile.provider", "Provider"),
                new ExternalModeValue<>("mapLinkProvider", this::selectedMapLinkProvider,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), value, profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders())),
                        providers, providerLabels));
        provider.setI18nEnabled(false, false);
        provider.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(provider, Category.MAPLINK, "maplink provider bluemap dynmap liveatlas pl3x squaremap players json"));

        SliderSetting<Long> refresh = new SliderSetting<>(tr("gui.combatant.map.maplink.profile.refresh", "Refresh interval (ms)"),
                new ExternalLongValue("mapLinkRefresh", this::selectedMapLinkRefresh,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), value, profile.defaultY(), profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders())),
                        500L, 120000L));
        refresh.setI18nEnabled(false, false);
        refresh.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(refresh, Category.MAPLINK, "maplink refresh interval polling"));

        SliderSetting<Integer> defaultY = new SliderSetting<>(tr("gui.combatant.map.maplink.profile.default_y", "Fallback Y"),
                new ExternalIntegerRangeValue("mapLinkDefaultY", this::selectedMapLinkDefaultY,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), value, profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders())),
                        -64, 320));
        defaultY.setI18nEnabled(false, false);
        defaultY.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(defaultY, Category.MAPLINK, "maplink fallback default y height"));

        SliderSetting<Integer> priority = new SliderSetting<>(tr("gui.combatant.map.maplink.profile.priority", "Source priority"),
                new ExternalIntegerRangeValue("mapLinkPriority", this::selectedMapLinkPriority,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), value, profile.dimensionMappings(), profile.requestHeaders())),
                        -100, 100));
        priority.setI18nEnabled(false, false);
        priority.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(priority, Category.MAPLINK, "maplink source priority location source"));

        TextListSetting mappings = new TextListSetting(tr("gui.combatant.map.maplink.profile.mappings", "World mappings"),
                new ExternalMapSetValue("mapLinkMappings", this::selectedMapLinkMappings,
                        value -> updateSelectedMapLinkProfile(profile -> copyMapLinkProfile(profile, profile.displayName(), profile.enabled(), profile.serverMatcher(), profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(), profile.sourcePriority(), value, profile.requestHeaders()))),
                TextListSetting.PickerMode.TEXT);
        mappings.setI18nEnabled(false, false);
        mappings.visibleWhen(this::hasSelectedMapLinkProfile);
        entries.add(new Entry(mappings, Category.MAPLINK, "maplink world mappings dimension provider world"));

        entries.add(new Entry(new SectionHeaderSetting(
                tr("gui.combatant.map.maplink.section.network", "Networking"),
                tr("gui.combatant.map.maplink.section.network.description", "Global timeout, stale-data and retry policy for all MapLink profiles.")),
                Category.MAPLINK, "maplink networking timeout stale backoff"));
        addSubsystemSettings(MapLinkConfig.get(), Category.MAPLINK);
    }

    private void addSubsystemSetting(combatant.client.config.subsystem.SubsystemConfig config, Category category, String id) {
        if (config == null || id == null) return;
        for (var def : config.getSettingDefs()) {
            if (def == null || !id.equals(def.getId())) continue;
            Setting setting = SettingFactory.fromDef(def);
            if (setting == null) return;
            setting.setParent(config);
            entries.add(new Entry(setting, category, (def.getId() + " " + setting.getName()).toLowerCase(Locale.ROOT)));
            return;
        }
    }

    private boolean hasSelectedMapLinkProfile() {
        return selectedMapLinkProfile() != null;
    }

    private MapLinkProfile selectedMapLinkProfile() {
        if (selectedMapLinkProfileId == null || selectedMapLinkProfileId.isBlank()) return null;
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) {
            if (profile.id().equals(selectedMapLinkProfileId)) return profile;
        }
        return null;
    }

    private void selectMapLinkProfile(String id) {
        selectedMapLinkProfileId = id == null ? "" : id;
        contentAnim = 0.0f;
    }

    private void updateSelectedMapLinkProfile(UnaryOperator<MapLinkProfile> update) {
        MapLinkProfile current = selectedMapLinkProfile();
        if (current == null || update == null) return;
        List<MapLinkProfile> next = new ArrayList<>(MapLinkConfig.get().allProfiles().size());
        boolean changed = false;
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) {
            if (!profile.id().equals(current.id())) {
                next.add(profile);
                continue;
            }
            MapLinkProfile replacement = update.apply(profile);
            next.add(replacement == null ? profile : replacement);
            changed = replacement != null && !replacement.equals(profile);
        }
        if (changed) MapLinkConfig.get().setProfiles(next);
    }

    private void createMapLinkProfileForCurrentServer() {
        Minecraft mc = Minecraft.getInstance();
        String server = mc != null && mc.getCurrentServer() != null && mc.getCurrentServer().ip != null
                ? mc.getCurrentServer().ip.trim() : "";
        if (server.isBlank()) return;
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) {
            if (MapLinkServerMatcher.matches(profile.serverMatcher(), server)) {
                selectMapLinkProfile(profile.id());
                return;
            }
        }
        String hostLabel = server;
        String baseId = sanitizeProfileId(server);
        Set<String> used = new LinkedHashSet<>();
        for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) used.add(profile.id());
        String id = baseId;
        int suffix = 2;
        while (used.contains(id)) id = baseId + '-' + suffix++;
        MapLinkProfile created = new MapLinkProfile(id, hostLabel, false, server, "",
                MapLinkProviderType.PLAYERS_JSON, 10_000L, 64, 0, Map.of(), Map.of());
        List<MapLinkProfile> next = new ArrayList<>(MapLinkConfig.get().allProfiles());
        next.add(created);
        MapLinkConfig.get().setProfiles(next);
        selectMapLinkProfile(created.id());
    }

    private void deleteSelectedMapLinkProfile() {
        MapLinkProfile selected = selectedMapLinkProfile();
        if (selected == null) return;
        List<MapLinkProfile> next = MapLinkConfig.get().allProfiles().stream()
                .filter(profile -> !profile.id().equals(selected.id())).toList();
        MapLinkConfig.get().setProfiles(next);
        selectedMapLinkProfileId = next.isEmpty() ? "" : next.getFirst().id();
        contentAnim = 0.0f;
    }

    private static String sanitizeProfileId(String raw) {
        String source = raw == null ? "server" : raw.trim().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(source.length());
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') out.append(c);
            else out.append('-');
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') out.setLength(out.length() - 1);
        return out.isEmpty() ? "server" : out.toString();
    }

    private static MapLinkProfile copyMapLinkProfile(MapLinkProfile source, String displayName, boolean enabled,
                                                     String serverMatcher, String baseUrl, MapLinkProviderType providerType,
                                                     long refreshIntervalMs, int defaultY, int sourcePriority,
                                                     Map<String, String> mappings, Map<String, String> headers) {
        return new MapLinkProfile(source.id(), displayName, enabled, serverMatcher, baseUrl, providerType,
                refreshIntervalMs, defaultY, sourcePriority, mappings, headers);
    }

    private String selectedMapLinkDisplayName() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? "" : p.displayName(); }
    private String selectedMapLinkServerMatcher() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? "" : p.serverMatcher(); }
    private String selectedMapLinkBaseUrl() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? "" : p.baseUrl(); }
    private MapLinkProviderType selectedMapLinkProvider() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? MapLinkProviderType.PLAYERS_JSON : p.providerType(); }
    private long selectedMapLinkRefresh() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? 10_000L : p.refreshIntervalMs(); }
    private int selectedMapLinkDefaultY() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? 64 : p.defaultY(); }
    private int selectedMapLinkPriority() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? 0 : p.sourcePriority(); }
    private Map<String, String> selectedMapLinkMappings() { MapLinkProfile p = selectedMapLinkProfile(); return p == null ? Map.of() : p.dimensionMappings(); }

    private static String playerLocationSourceLabel(PlayerLocationSource source) {
        if (source == null) return tr("gui.combatant.map.source.unknown", "unknown");
        return switch (source) {
            case LOCAL_ENTITY_EXACT -> tr("gui.combatant.map.source.local", "local");
            case MAPLINK_EXACT -> tr("gui.combatant.map.source.maplink", "MapLink");
            case LOCATOR_EXACT -> tr("gui.combatant.map.source.locator_exact", "locator exact");
            case DUPLEX_TRIANGULATED -> tr("gui.combatant.map.source.duplex", "duplex");
            case TRIANGULATED -> tr("gui.combatant.map.source.triangulated", "triangulated");
            case LOCATOR_BEARING -> tr("gui.combatant.map.source.bearing", "locator bearing");
            case LOCATOR_APPROXIMATE -> tr("gui.combatant.map.source.locator_approx", "locator approximate");
            case HISTORICAL -> tr("gui.combatant.map.source.history", "history");
        };
    }

    private static String mapLinkStatusLabel(MapLinkProfileStatus status) {
        if (status == null) return tr("gui.combatant.map.maplink.status.idle", "Idle");
        return switch (status) {
            case DISABLED -> tr("gui.combatant.map.maplink.status.disabled", "Disabled");
            case IDLE -> tr("gui.combatant.map.maplink.status.idle", "Idle");
            case CONNECTING -> tr("gui.combatant.map.maplink.status.connecting", "Connecting");
            case LIVE -> tr("gui.combatant.map.maplink.status.live", "Live");
            case STALE -> tr("gui.combatant.map.maplink.status.stale", "Stale");
            case AUTH_ERROR -> tr("gui.combatant.map.maplink.status.auth_error", "Auth error");
            case HTTP_ERROR -> tr("gui.combatant.map.maplink.status.http_error", "HTTP error");
            case PARSE_ERROR -> tr("gui.combatant.map.maplink.status.parse_error", "Parse error");
            case WORLD_UNMAPPED -> tr("gui.combatant.map.maplink.status.world_unmapped", "World unmapped");
        };
    }

    private static String mapLinkProviderLabel(MapLinkProviderType type) {
        if (type == null) return "Players JSON";
        return switch (type) {
            case BLUEMAP -> "BlueMap";
            case DYNMAP -> "Dynmap";
            case LIVEATLAS -> "LiveAtlas";
            case PL3XMAP -> "Pl3xMap";
            case PLAYERS_JSON -> "Players JSON";
            case SQUAREMAP -> "SquareMap";
        };
    }

    private void auditXaeroCoverage() {
        auditXaeroOptionClass(WorldMapProfiledConfigOptions.class, Set.of());
        auditXaeroOptionClass(WorldMapPrimaryClientConfigOptions.class, Set.of(
                WorldMapPrimaryClientConfigOptions.IGNORED_UPDATE,
                WorldMapPrimaryClientConfigOptions.RELOAD_VIEWED_VERSION,
                WorldMapPrimaryClientConfigOptions.GLOBAL_VERSION
        ));
    }

    private void auditXaeroOptionClass(Class<?> owner, Set<ConfigOption<?>> ignored) {
        try {
            for (var field : owner.getFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                Object value = field.get(null);
                if (!(value instanceof ConfigOption<?> option) || ignored.contains(option)) continue;
                if (boundXaeroOptions.contains(option)) continue;
                DebugLog.warnOnce("clickgui-map-xaero-unbound-" + owner.getSimpleName() + '-' + field.getName(),
                        "Xaero World Map option is not exposed in Combatant Browser: "
                                + owner.getSimpleName() + '.' + field.getName());
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            DebugLog.warnOnce("clickgui-map-xaero-coverage-" + owner.getSimpleName(),
                    "Failed to audit Xaero World Map option coverage for " + owner.getSimpleName(), error);
        }
    }

    private static boolean isExplicitModeOption(ConfigOption<?> option) {
        return option == WorldMapProfiledConfigOptions.BLOCK_COLORS
                || option == WorldMapProfiledConfigOptions.TERRAIN_SLOPES
                || option == WorldMapProfiledConfigOptions.AUTO_CAVE_MODE
                || option == WorldMapProfiledConfigOptions.ARROW_COLOR
                || option == WorldMapProfiledConfigOptions.DEFAULT_CAVE_MODE_TYPE;
    }

    private static <T> Setting modeSetting(String name, ConfigOption<T> option,
                                           IndexedConfigOption<?> indexed,
                                           Supplier<T> getter, Consumer<T> setter) {
        List<T> values = indexed.getValidValues().stream().map(XaeroMapSettingsPanel::<T>cast).toList();
        List<String> labels = new ArrayList<>(values.size());
        for (T value : values) {
            var display = option.getDisplayGetter().apply(option, value);
            String label = display == null ? String.valueOf(value) : LegacyTextUtil.stripLegacy(display.getString());
            label = label == null || label.isBlank() ? String.valueOf(value) : label.trim();
            if (labels.contains(label)) label = label + " (" + value + ')';
            labels.add(label);
        }
        return new ModeSetting(name, new ExternalModeValue<>(option.getId(), getter, setter, values, labels));
    }

    private static String searchText(ConfigOption<?> option, String name) {
        return ((name == null ? "" : name) + ' ' + (option.getId() == null ? "" : option.getId()))
                .toLowerCase(Locale.ROOT);
    }

    private static String resolveDisplayName(ConfigOption<?> option) {
        var component = option.getDisplayName();
        if (component != null) {
            String displayName = LegacyTextUtil.stripLegacy(component.getString());
            if (displayName != null && !displayName.isBlank()) return displayName.trim();
        }
        return humanizeOptionId(option.getId());
    }

    private static String humanizeOptionId(String id) {
        if (id == null || id.isBlank()) return "Xaero setting";
        String[] words = id.trim().replace('.', '_').replace('-', '_').split("_+");
        StringBuilder out = new StringBuilder(id.length() + 4);
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) out.append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.isEmpty() ? id : out.toString();
    }

    private static String hex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigOption<T> raw(ConfigOption<?> option) { return (ConfigOption<T>) option; }
    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) { return (T) value; }
    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private static String tr(String key, String fallback) {
        String translated;
        try {
            translated = I18n.get(key, "");
        } catch (RuntimeException ignored) {
            translated = null;
        }
        if (translated == null || translated.equals(key) || translated.startsWith("Format error:")) {
            translated = fallback;
        }
        return LegacyTextUtil.stripLegacy(translated).replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static float easeOutCubic(float value) {
        float t = clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static float smootherStep(float value) {
        float t = clamp(value, 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private enum Category {
        DISPLAY("display", "Display", "Map chrome, coordinates, arrow and footprints", "map"),
        TERRAIN("terrain", "Terrain", "Terrain colors, lighting and chunk updates", "layers"),
        WAYPOINTS("waypoints", "Waypoints", "Waypoint rendering and visibility", "map-pinned"),
        CAVE("cave", "Cave", "Xaero cave mode, depth and start layer", "land-plot"),
        PLAYERS("players", "Players & Radar", "Xaero tracked players, radar entities and claims", "users-round"),
        TARGETS("targets", "Targets", "Target players and live location sources", "crosshair"),
        TRIANGULATION("triangulation", "Triangulation", "Collection mode, solver quality and live estimates", "radar"),
        MAPLINK("maplink", "MapLink", "Per-server web-map profiles and network status", "map"),
        NAVIGATION("navigation", "Navigation", "Teleport and navigation behaviour", "route"),
        EXPORT("export", "Export", "World Map export behaviour", "map"),
        ADVANCED("advanced", "Advanced", "Loading budget and technical options", "settings-2");

        final String id;
        final String fallbackLabel;
        final String fallbackDescription;
        final String icon;
        Category(String id, String fallbackLabel, String fallbackDescription, String icon) {
            this.id = id;
            this.fallbackLabel = fallbackLabel;
            this.fallbackDescription = fallbackDescription;
            this.icon = icon;
        }
        String label() { return tr("gui.combatant.map.category." + id, fallbackLabel); }
        String description() { return tr("gui.combatant.map.category." + id + ".description", fallbackDescription); }
    }

    private record SolidBrowserLayout(float width, float height,
                                      float navWidth, float collectionWidth,
                                      float detailWidth, float detailX,
                                      float headerHeight, float bodyHeight,
                                      float detailViewportWidth, float detailViewportHeight,
                                      float navX, float navStartY, float navRowWidth,
                                      float navRowHeight, float navRowGap,
                                      float toolbarLeftX, float toolbarY,
                                      float searchWidth, float searchHeight,
                                      float closeX, float closeY, float closeWidth, float closeHeight,
                                      float detailHeaderX, float detailHeaderY,
                                      float detailHeaderWidth, float detailHeaderHeight,
                                      float contentX, float contentY, float contentWidth, float contentHeight,
                                      float navSeparatorX, float navSeparatorY, float navSeparatorHeight,
                                      float headerSeparatorX, float headerSeparatorY, float headerSeparatorWidth) {
        static SolidBrowserLayout of(float width, float height) {
            float nav = width * 268.0f / DESIGN_WIDTH;
            float collection = 0.0f;
            float header = HEADER_HEIGHT;
            float detail = Math.max(0.0f, width - nav - collection);
            float body = Math.max(0.0f, height - header);
            float navGap = 6.0f;
            float navRowHeight = Math.max(32.0f, Math.min(46.0f,
                    (body - BASE_INSET * 2.0f - navGap * (Category.values().length - 1))
                            / Category.values().length));
            float navStackHeight = Category.values().length * navRowHeight
                    + (Category.values().length - 1) * navGap;
            float navStartY = header + Math.max(BASE_INSET, (body - navStackHeight) * 0.5f);
            float detailViewportWidth = Math.max(240.0f, detail - BASE_INSET * 2.0f);
            float detailHeaderY = header + BASE_INSET;
            float detailHeaderHeight = 44.0f;
            float contentY = detailHeaderY + detailHeaderHeight + 11.0f;
            return new SolidBrowserLayout(
                    width, height, nav, collection, detail, nav + collection, header, body,
                    detailViewportWidth,
                    Math.max(160.0f, body - BASE_INSET * 2.0f),
                    BASE_INSET, navStartY, Math.max(1.0f, nav - BASE_INSET * 2.0f),
                    navRowHeight, navGap,
                    BASE_INSET, (header - SEARCH_HEIGHT) * 0.5f,
                    SEARCH_WIDTH, SEARCH_HEIGHT,
                    Math.max(0.0f, detail - BASE_INSET - CLOSE_SIZE),
                    (header - CLOSE_SIZE) * 0.5f, CLOSE_SIZE, CLOSE_SIZE,
                    BASE_INSET, detailHeaderY, detailViewportWidth, detailHeaderHeight,
                    BASE_INSET, contentY, detailViewportWidth,
                    Math.max(120.0f, height - contentY - BASE_INSET),
                    nav - 1.0f, BASE_INSET, Math.max(0.0f, height - BASE_INSET * 2.0f),
                    nav + collection + BASE_INSET, header - 1.0f,
                    Math.max(0.0f, detail - BASE_INSET * 2.0f)
            );
        }

        LinkedHashMap<String, Object> toProps() {
            LinkedHashMap<String, Object> props = new LinkedHashMap<>();
            props.put("width", width);
            props.put("height", height);
            props.put("navWidth", navWidth);
            props.put("collectionWidth", collectionWidth);
            props.put("detailWidth", detailWidth);
            props.put("headerHeight", headerHeight);
            props.put("collectionX", navWidth);
            props.put("detailX", detailX);
            props.put("bodyHeight", bodyHeight);
            props.put("detailViewportWidth", detailViewportWidth);
            props.put("detailViewportHeight", detailViewportHeight);
            props.put("navStartY", navStartY);
            props.put("navX", navX);
            props.put("navRowWidth", navRowWidth);
            props.put("navRowHeight", navRowHeight);
            props.put("navRowGap", navRowGap);
            props.put("toolbarLeftX", toolbarLeftX);
            props.put("toolbarY", toolbarY);
            props.put("searchWidth", searchWidth);
            props.put("searchHeight", searchHeight);
            props.put("closeX", closeX);
            props.put("closeY", closeY);
            props.put("closeWidth", closeWidth);
            props.put("closeHeight", closeHeight);
            props.put("detailHeaderX", detailHeaderX);
            props.put("detailHeaderY", detailHeaderY);
            props.put("detailHeaderWidth", detailHeaderWidth);
            props.put("detailHeaderHeight", detailHeaderHeight);
            props.put("contentX", contentX);
            props.put("contentY", contentY);
            props.put("contentWidth", contentWidth);
            props.put("contentHeight", contentHeight);
            props.put("navSeparatorX", navSeparatorX);
            props.put("navSeparatorY", navSeparatorY);
            props.put("navSeparatorHeight", navSeparatorHeight);
            props.put("headerSeparatorX", headerSeparatorX);
            props.put("headerSeparatorY", headerSeparatorY);
            props.put("headerSeparatorWidth", headerSeparatorWidth);
            return props;
        }
    }
    private record Entry(Setting setting, Category category, String searchText) {}
    private record Hit(Setting setting, float x, float y, float width, float height) {}
    private record CategoryHit(Category category, float x, float y, float width, float height) {
        boolean contains(float mx, float my) { return inside(mx, my, x, y, width, height); }
    }
    private static final class ExternalIdentifierSetValue extends SetValue {
        private final Supplier<Set<Identifier>> getter;
        private final Consumer<Set<Identifier>> setter;
        private ExternalIdentifierSetValue(String name, Supplier<Set<Identifier>> getter, Consumer<Set<Identifier>> setter) {
            super(name);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Set<String> get() {
            Set<Identifier> current = getter.get();
            LinkedHashSet<String> out = new LinkedHashSet<>();
            if (current != null) for (Identifier id : current) if (id != null) out.add(id.toString());
            return out;
        }
        @Override public void set(Set<String> values) {
            LinkedHashSet<Identifier> parsed = new LinkedHashSet<>();
            if (values != null) for (String raw : values) {
                Identifier id = raw == null ? null : Identifier.tryParse(raw.trim());
                if (id != null) parsed.add(id);
            }
            setter.accept(parsed);
        }
        @Override public Object toJson() { return new ArrayList<>(get()); }
        @Override public void fromJson(Object json) {
            if (!(json instanceof List<?> list)) return;
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (Object value : list) if (value instanceof String text) values.add(text);
            set(values);
        }
    }

    private static final class ExternalCaveStartValue extends NumberValue<Integer> {
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private ExternalCaveStartValue(String name, Supplier<Integer> getter, Consumer<Integer> setter) {
            super(name, getter.get(), -64, 320);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Integer get() { return getter.get(); }
        @Override public void set(Integer value) { setter.accept(value == null ? 320 : value); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number n) set(n.intValue()); }
        @Override public String toDisplay() { return get() >= 320 ? "Auto" : Integer.toString(get()); }
    }

    private interface DynamicSearchEntry {
        String dynamicSearchText();
    }

    private static final class SectionHeaderSetting extends Setting {
        private final String title;
        private final String description;

        private SectionHeaderSetting(String title, String description) {
            this(title, description, null);
        }

        private SectionHeaderSetting(String title, String description, Supplier<Boolean> visible) {
            super(title == null ? "" : title);
            this.title = title == null ? "" : title;
            this.description = description == null ? "" : description;
            setI18nEnabled(false, false);
            if (visible != null) visibleWhen(visible);
        }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), title,
                    x + 4.0f, y + 2.0f, 19.5f, palette.panelText(), false);
            if (!description.isBlank()) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), description,
                        x + 4.0f, y + 25.0f, 13.5f, palette.panelMuted(), false);
            }
            ClickGuiRenderer.drawRect(x + 4.0f, y + 48.0f, Math.max(1.0f, width - 8.0f), 1.0f,
                    SettingsGuiPalette.withAlpha(palette.panelText(), 26));
        }

        @Override public void mouseClicked(double mx, double my, int button) {}
        @Override public float getHeight() { return description.isBlank() ? 34.0f : 52.0f; }
    }

    private final class TriangulationModeSetting extends Setting {
        private static final float CARD_H = 86.0f;
        private final List<ModeHit> modeHits = new ArrayList<>();
        private final Map<MapTriangulationMode, Float> hoverAnims = new LinkedHashMap<>();

        private TriangulationModeSetting() {
            super("Triangulation mode");
            setI18nEnabled(false, false);
        }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            modeHits.clear();
            SettingsGuiPalette palette = SettingsGuiPalette.current();
            MapTriangulationMode selected = MapTriangulationConfig.get().mode();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    tr("gui.combatant.map.triangulation.mode.title", "Tracking mode"),
                    x + 4.0f, y + 2.0f, 21.0f, palette.panelText(), false);
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                    tr("gui.combatant.map.triangulation.mode.description", "Choose what the solver is allowed to collect. This does not change MapLink exact positions."),
                    x + 4.0f, y + 27.0f, 13.5f, palette.panelMuted(), false);

            float gap = 10.0f;
            float cardW = Math.max(120.0f, (width - gap * 2.0f) / 3.0f);
            float cardY = y + 52.0f;
            MapTriangulationMode[] modes = {MapTriangulationMode.OFF, MapTriangulationMode.DATA_MINING, MapTriangulationMode.TARGETED};
            for (int i = 0; i < modes.length; i++) {
                MapTriangulationMode mode = modes[i];
                float cx = x + i * (cardW + gap);
                boolean hover = inside(mouseX, mouseY, cx, cardY, cardW, CARD_H);
                float hoverAnim = AnimationUtility.approach(hoverAnims.getOrDefault(mode, 0.0f), hover ? 1.0f : 0.0f,
                        AnimationUtility.deltaTime(), hover ? 13.0f : 9.0f);
                hoverAnims.put(mode, hoverAnim);
                boolean active = selected == mode;
                int bg = active
                        ? SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(36.0f + hoverAnim * 12.0f))
                        : SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(8.0f + hoverAnim * 12.0f));
                ClickGuiRenderer.drawRoundedRect(cx, cardY, cardW, CARD_H, 9.0f, bg);
                if (active) {
                    ClickGuiRenderer.drawRect(cx, cardY + 12.0f, 2.5f, CARD_H - 24.0f,
                            SettingsGuiPalette.withAlpha(Theme.theme().accent(), 230));
                }
                String title = triangulationModeTitle(mode);
                String desc = triangulationModeDescription(mode);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), title,
                        cx + 12.0f, cardY + 10.0f, 17.0f, active ? Theme.theme().accent() : palette.panelText(), false);
                drawWrappedText(desc, cx + 12.0f, cardY + 33.0f, cardW - 24.0f, 13.0f, palette.panelMuted(), 2);
                modeHits.add(new ModeHit(mode, cx, cardY, cardW, CARD_H));
            }
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            for (ModeHit hit : modeHits) {
                if (!inside((float) mx, (float) my, hit.x, hit.y, hit.w, hit.h)) continue;
                MapTriangulationConfig.get().setMode(hit.mode);
                contentAnim = 0.0f;
                return;
            }
        }

        @Override public float getHeight() { return 148.0f; }

        private record ModeHit(MapTriangulationMode mode, float x, float y, float w, float h) {}
    }

    private static String triangulationModeTitle(MapTriangulationMode mode) {
        return switch (mode) {
            case OFF -> tr("gui.combatant.map.triangulation.mode.off", "Off");
            case DATA_MINING -> tr("gui.combatant.map.triangulation.mode.data_mining", "Data Mining");
            case TARGETED -> tr("gui.combatant.map.triangulation.mode.targeted", "Targeted");
        };
    }

    private static String triangulationModeDescription(MapTriangulationMode mode) {
        return switch (mode) {
            case OFF -> tr("gui.combatant.map.triangulation.mode.off.description", "Do not collect locator bearings or run the solver.");
            case DATA_MINING -> tr("gui.combatant.map.triangulation.mode.data_mining.description", "Track all eligible players at a bounded background rate.");
            case TARGETED -> tr("gui.combatant.map.triangulation.mode.targeted.description", "Spend solver budget only on players selected in Targets.");
        };
    }

    private static void drawWrappedText(String text, float x, float y, float maxWidth, float size, int color, int maxLines) {
        if (text == null || text.isBlank()) return;
        String[] words = text.trim().split("\\s+");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            float width = ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), candidate, size);
            if (width > maxWidth && !line.isEmpty()) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), line.toString(), x, y + lines * (size + 3.0f), size, color, false);
                lines++;
                if (lines >= maxLines) return;
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty() && lines < maxLines) {
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), line.toString(), x, y + lines * (size + 3.0f), size, color, false);
        }
    }

    private final class TargetPlayersSetting extends Setting implements DynamicSearchEntry {
        private static final float ROW_H = 44.0f;
        private static final int MAX_ROWS = 8;
        private final List<TargetHit> rowHits = new ArrayList<>();
        private final Map<UUID, Float> hoverAnims = new LinkedHashMap<>();

        private TargetPlayersSetting() { super(tr("gui.combatant.map.targets.title", "Target players")); }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            rowHits.clear();
            var palette = SettingsGuiPalette.current();
            List<TargetRow> players = displayTargetRows();
            int rows = Math.min(MAX_ROWS, players.size());

            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.targets.title", "Target players"),
                    x + 6.0f, y + 4.0f, 20.0f, palette.panelText(), false);
            String summary = triangulationModeTitle(MapTriangulationConfig.get().mode())
                    + "  ·  " + targetedCount() + " " + tr("gui.combatant.map.targets.targeted", "targeted")
                    + "  ·  " + onlineCount() + " " + tr("gui.combatant.map.targets.online", "online");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), summary,
                    x + 6.0f, y + 28.0f, 13.5f, palette.panelMuted(), false);

            float rowY = y + 54.0f;
            if (rows == 0) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        tr("gui.combatant.map.targets.empty", "No online, observed or saved targets"), x + 6.0f, rowY + 8.0f,
                        15.0f, palette.panelMuted(), false);
                return;
            }

            long now = System.currentTimeMillis();
            for (int i = 0; i < rows; i++) {
                TargetRow row = players.get(i);
                boolean targeted = MapTriangulationConfig.get().isTargeted(row.id());
                float ry = rowY + i * ROW_H;
                float rw = Math.max(1.0f, width - 8.0f);
                boolean hover = inside(mouseX, mouseY, x + 4.0f, ry, rw, ROW_H - 3.0f);
                float hoverAnim = AnimationUtility.approach(hoverAnims.getOrDefault(row.id(), 0.0f), hover ? 1.0f : 0.0f,
                        AnimationUtility.deltaTime(), hover ? 13.0f : 8.0f);
                hoverAnims.put(row.id(), hoverAnim);

                int base = targeted
                        ? SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(28.0f + hoverAnim * 16.0f))
                        : SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(7.0f + hoverAnim * 13.0f));
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, ry, rw, ROW_H - 3.0f, 6.0f, base);
                if (targeted) {
                    ClickGuiRenderer.drawRect(x + 4.0f, ry + 5.0f, 2.0f, ROW_H - 13.0f,
                            SettingsGuiPalette.withAlpha(Theme.theme().accent(), 220));
                }

                String name = row.name().isBlank() ? row.id().toString().substring(0, 8) : row.name();
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), name,
                        x + 14.0f, ry + 7.0f, 16.0f, palette.panelText(), false);

                String meta = targetMeta(row, now);
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), meta,
                        x + 14.0f, ry + 25.0f, 12.5f, palette.panelMuted(), false);

                String action = targeted ? tr("gui.combatant.map.targets.action.targeted", "Targeted") : tr("gui.combatant.map.targets.action.add", "Add");
                float actionW = targeted ? 76.0f : 52.0f;
                float actionX = x + width - actionW - 12.0f;
                int actionColor = targeted ? Theme.theme().accent() : palette.panelText();
                ClickGuiRenderer.drawRoundedRect(actionX, ry + 9.0f, actionW, 25.0f, 7.0f,
                        targeted ? SettingsGuiPalette.withAlpha(Theme.theme().accent(), 30) : SettingsGuiPalette.withAlpha(palette.panelText(), 10));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), action,
                        actionX + 10.0f, ry + 15.0f, 12.5f, actionColor, false);
                rowHits.add(new TargetHit(row.id(), x + 4.0f, ry, rw, ROW_H - 3.0f));
            }
            if (players.size() > rows) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        "+" + (players.size() - rows) + " " + tr("gui.combatant.map.common.more_search", "more · use search to narrow"),
                        x + 10.0f, rowY + rows * ROW_H + 1.0f, 11.5f, palette.panelMuted(), false);
            }
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            for (TargetHit hit : rowHits) {
                if (!inside((float) mx, (float) my, hit.x, hit.y, hit.w, hit.h)) continue;
                MapTriangulationConfig config = MapTriangulationConfig.get();
                if (config.isTargeted(hit.id)) config.removeTargetedPlayer(hit.id);
                else config.addTargetedPlayer(hit.id);
                return;
            }
        }

        @Override
        public float getHeight() {
            List<TargetRow> rows = displayTargetRows();
            int shown = Math.min(MAX_ROWS, rows.size());
            float footer = rows.size() > shown ? 18.0f : 0.0f;
            return 56.0f + Math.max(1, shown) * ROW_H + footer;
        }

        @Override
        public String dynamicSearchText() {
            StringBuilder out = new StringBuilder();
            for (TargetRow row : collectTargetRows()) out.append(' ').append(targetSearchText(row));
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private List<TargetRow> displayTargetRows() {
            List<TargetRow> rows = collectTargetRows();
            String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || "target players targeted locator triangulation tracking player list".contains(needle)) {
                return rows;
            }
            return rows.stream().filter(row -> targetSearchText(row).contains(needle)).toList();
        }

        private static String targetSearchText(TargetRow row) {
            StringBuilder out = new StringBuilder().append(row.id()).append(' ').append(row.name());
            if (row.snapshot() != null) out.append(' ').append(row.snapshot().source().name());
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private List<TargetRow> collectTargetRows() {
            LinkedHashMap<UUID, TargetRow> rows = new LinkedHashMap<>();
            for (Map.Entry<UUID, PlayerLocationSnapshot> entry : PlayerLocationService.get().snapshot().bestByPlayer().entrySet()) {
                PlayerLocationSnapshot snapshot = entry.getValue();
                String name = snapshot == null ? "" : snapshot.playerName();
                rows.put(entry.getKey(), new TargetRow(entry.getKey(), name == null ? "" : name, false, snapshot));
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) {
                    if (info == null || info.getProfile() == null || info.getProfile().id() == null) continue;
                    UUID id = info.getProfile().id();
                    String name = info.getProfile().name() == null ? "" : info.getProfile().name();
                    TargetRow previous = rows.get(id);
                    rows.put(id, new TargetRow(id, name, true, previous == null ? null : previous.snapshot()));
                }
            }

            Set<String> saved = MapTriangulationConfig.get().targetedPlayersValue().get();
            if (saved != null) {
                for (String raw : saved) {
                    if (raw == null || raw.isBlank()) continue;
                    try {
                        UUID id = UUID.fromString(raw.trim());
                        rows.putIfAbsent(id, new TargetRow(id, "", false, null));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            List<TargetRow> out = new ArrayList<>(rows.values());
            out.sort(Comparator
                    .comparing((TargetRow row) -> !MapTriangulationConfig.get().isTargeted(row.id()))
                    .thenComparing((TargetRow row) -> !row.online())
                    .thenComparing((TargetRow row) -> row.snapshot() == null ? 1 : 0)
                    .thenComparing(row -> row.name().toLowerCase(Locale.ROOT)));
            return out;
        }

        private String targetMeta(TargetRow row, long now) {
            PlayerLocationSnapshot snapshot = row.snapshot();
            if (snapshot == null) return row.online()
                    ? tr("gui.combatant.map.targets.meta.waiting", "online · waiting for location source")
                    : tr("gui.combatant.map.targets.meta.unseen", "saved target · currently unseen");
            long age = snapshot.ageMs(now);
            String ageText = age < 1000L ? tr("gui.combatant.map.common.now", "now") : (age / 1000L) + "s";
            String source = playerLocationSourceLabel(snapshot.source());
            if (!snapshot.hasPosition()) {
                return source + " · " + tr("gui.combatant.map.targets.bearing", "bearing") + " · " + Math.round(snapshot.confidence() * 100.0) + "% · " + ageText;
            }
            return source + " · " + Math.round(snapshot.x()) + ", " + Math.round(snapshot.z())
                    + " · " + Math.round(snapshot.confidence() * 100.0) + "% · " + ageText;
        }

        private int targetedCount() {
            Set<String> values = MapTriangulationConfig.get().targetedPlayersValue().get();
            return values == null ? 0 : values.size();
        }

        private int onlineCount() {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.getConnection() != null ? mc.getConnection().getListedOnlinePlayers().size() : 0;
        }

        private record TargetRow(UUID id, String name, boolean online, PlayerLocationSnapshot snapshot) {}
        private record TargetHit(UUID id, float x, float y, float w, float h) {}
    }

    private final class TriangulationStatusSetting extends Setting implements DynamicSearchEntry {
        private static final float ROW_H = 36.0f;
        private static final int MAX_ESTIMATES = 5;

        private TriangulationStatusSetting() { super(tr("gui.combatant.map.triangulation.runtime.title", "Solver status")); }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            var palette = SettingsGuiPalette.current();
            HeuristicRuntime runtime = HeuristicRuntime.get();
            HeuristicRuntimeStats stats = runtime.stats();
            List<Map.Entry<UUID, HeuristicEstimate>> estimates = displayEstimates(runtime.snapshot());

            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.triangulation.runtime.title", "Triangulation runtime"),
                    x + 6.0f, y + 4.0f, 20.0f, palette.panelText(), false);
            String state = triangulationModeTitle(stats.mode())
                    + "  ·  " + stats.activeTargets() + " " + tr("gui.combatant.map.triangulation.runtime.active", "active")
                    + "  ·  " + stats.queuedTargets() + " " + tr("gui.combatant.map.triangulation.runtime.queued", "queued");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), state,
                    x + 6.0f, y + 29.0f, 13.5f, palette.panelMuted(), false);

            float metricsY = y + 52.0f;
            String metrics = stats.accepted() + " " + tr("gui.combatant.map.triangulation.runtime.accepted", "accepted")
                    + "  ·  " + stats.solved() + " " + tr("gui.combatant.map.triangulation.runtime.solved", "solved")
                    + "  ·  " + stats.rejectedInformationGain() + " " + tr("gui.combatant.map.triangulation.runtime.rejected", "low-gain rejected")
                    + "  ·  " + stats.segmentResets() + " " + tr("gui.combatant.map.triangulation.runtime.resets", "resets");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), metrics,
                    x + 6.0f, metricsY, 12.0f, palette.panelMuted(), false);

            float rowY = y + 78.0f;
            if (estimates.isEmpty()) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.triangulation.runtime.empty", "No stable estimates yet"),
                        x + 6.0f, rowY + 6.0f, 14.0f, palette.panelMuted(), false);
                return;
            }

            int rows = Math.min(MAX_ESTIMATES, estimates.size());
            long now = System.currentTimeMillis();
            Map<UUID, PlayerLocationSnapshot> names = PlayerLocationService.get().snapshot().bestByPlayer();
            for (int i = 0; i < rows; i++) {
                var entry = estimates.get(i);
                HeuristicEstimate estimate = entry.getValue();
                float ry = rowY + i * ROW_H;
                boolean hover = inside(mouseX, mouseY, x + 4.0f, ry, Math.max(1.0f, width - 8.0f), ROW_H - 2.0f);
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, ry, Math.max(1.0f, width - 8.0f), ROW_H - 2.0f,
                        5.5f, SettingsGuiPalette.withAlpha(palette.panelText(), hover ? 17 : 6));

                PlayerLocationSnapshot snapshot = names.get(entry.getKey());
                String name = snapshot == null || snapshot.playerName().isBlank()
                        ? entry.getKey().toString().substring(0, 8) : snapshot.playerName();
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), name,
                        x + 12.0f, ry + 7.0f, 15.0f, palette.panelText(), false);

                long age = Math.max(0L, now - estimate.updatedAtMs());
                String detail = "±" + Math.round(estimate.uncertaintyMajor()) + "  ·  "
                        + Math.round(estimate.confidence() * 100.0) + "%  ·  "
                        + estimate.inlierCount() + "/" + estimate.sampleCount() + " " + tr("gui.combatant.map.triangulation.runtime.samples", "samples") + "  ·  "
                        + (age < 1000 ? tr("gui.combatant.map.common.now", "now") : (age / 1000) + "s");
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), detail,
                        x + Math.max(170.0f, width * 0.40f), ry + 9.0f, 12.5f,
                        estimate.confidence() >= 0.65 ? Theme.theme().accent() : palette.panelMuted(), false);
            }
        }

        @Override public void mouseClicked(double mx, double my, int button) {}

        @Override
        public float getHeight() {
            int rows = Math.min(MAX_ESTIMATES, displayEstimates(HeuristicRuntime.get().snapshot()).size());
            return 80.0f + Math.max(1, rows) * ROW_H;
        }

        @Override
        public String dynamicSearchText() {
            StringBuilder out = new StringBuilder();
            for (UUID id : HeuristicRuntime.get().snapshot().keySet()) out.append(' ').append(id);
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private List<Map.Entry<UUID, HeuristicEstimate>> displayEstimates(Map<UUID, HeuristicEstimate> values) {
            List<Map.Entry<UUID, HeuristicEstimate>> rows = sortedEstimates(values);
            String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || "triangulation solver status observations accepted rejected confidence uncertainty estimates data mining targeted".contains(needle)) {
                return rows;
            }
            Map<UUID, PlayerLocationSnapshot> names = PlayerLocationService.get().snapshot().bestByPlayer();
            return rows.stream().filter(entry -> {
                PlayerLocationSnapshot snapshot = names.get(entry.getKey());
                String name = snapshot == null ? "" : snapshot.playerName();
                return (entry.getKey() + " " + name).toLowerCase(Locale.ROOT).contains(needle);
            }).toList();
        }

        private static List<Map.Entry<UUID, HeuristicEstimate>> sortedEstimates(Map<UUID, HeuristicEstimate> values) {
            List<Map.Entry<UUID, HeuristicEstimate>> out = new ArrayList<>(values.entrySet());
            out.sort(Comparator
                    .comparing((Map.Entry<UUID, HeuristicEstimate> entry) -> !MapTriangulationConfig.get().isTargeted(entry.getKey()))
                    .thenComparing(Comparator.comparingDouble((Map.Entry<UUID, HeuristicEstimate> entry) -> entry.getValue().confidence()).reversed())
                    .thenComparingLong(entry -> -entry.getValue().updatedAtMs()));
            return out;
        }
    }

    private final class MapLinkStatusSetting extends Setting implements DynamicSearchEntry {
        private static final float ROW_H = 52.0f;
        private static final int MAX_ROWS = 6;
        private final List<MapLinkHit> rowHits = new ArrayList<>();
        private final Map<String, Float> hoverAnims = new LinkedHashMap<>();
        private float createX, createY, createW, createH;
        private float deleteX, deleteY, deleteW, deleteH;

        private MapLinkStatusSetting() { super(tr("gui.combatant.map.maplink.profiles.title", "MapLink profiles")); }

        @Override
        public void render(float x, float y, float width, float mouseX, float mouseY) {
            rowHits.clear();
            var palette = SettingsGuiPalette.current();
            MapLinkSnapshot snapshot = MapLinkRuntime.get().snapshot();
            Map<String, MapLinkProfileState> states = snapshot.profileStates();
            List<MapLinkProfile> profiles = displayMapLinkProfiles();

            String server = currentServerAddress();
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.maplink.profiles.title", "MapLink profiles"),
                    x + 6.0f, y + 4.0f, 20.0f, palette.panelText(), false);
            String summary = profiles.size() + " " + tr("gui.combatant.map.maplink.profiles.configured", "configured")
                    + "  ·  " + snapshot.observations().size() + " " + tr("gui.combatant.map.maplink.profiles.observations", "observations");
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), summary,
                    x + 6.0f, y + 29.0f, 13.5f, palette.panelMuted(), false);

            String createLabel = server.isBlank()
                    ? tr("gui.combatant.map.maplink.profiles.no_server", "Connect to a server to create a profile")
                    : tr("gui.combatant.map.maplink.profiles.create_current", "Create for current server");
            createW = Math.min(230.0f, Math.max(160.0f, ClickGuiRenderer.textWidth(ClickGuiRenderer.getOnestMedium(), createLabel, 13.0f) + 24.0f));
            createH = 30.0f;
            createX = x + width - createW - 6.0f;
            createY = y + 8.0f;
            boolean createHover = !server.isBlank() && inside(mouseX, mouseY, createX, createY, createW, createH);
            ClickGuiRenderer.drawRoundedRect(createX, createY, createW, createH, 8.0f,
                    server.isBlank() ? SettingsGuiPalette.withAlpha(palette.panelText(), 7)
                            : SettingsGuiPalette.withAlpha(Theme.theme().accent(), createHover ? 46 : 30));
            ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), createLabel,
                    createX + 12.0f, createY + 8.0f, 13.0f,
                    server.isBlank() ? palette.panelMuted() : Theme.theme().accent(), false);

            float rowY = y + 58.0f;
            if (profiles.isEmpty()) {
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, rowY, Math.max(1.0f, width - 8.0f), 74.0f, 9.0f,
                        SettingsGuiPalette.withAlpha(palette.panelText(), 7));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.maplink.profiles.empty", "No MapLink profile configured"),
                        x + 16.0f, rowY + 13.0f, 16.0f, palette.panelText(), false);
                drawWrappedText(tr("gui.combatant.map.maplink.profiles.empty.description",
                                "Create a profile for this Minecraft server, choose the web-map provider and paste its public map URL."),
                        x + 16.0f, rowY + 36.0f, width - 32.0f, 13.0f, palette.panelMuted(), 2);
                return;
            }

            int rows = Math.min(MAX_ROWS, profiles.size());
            for (int i = 0; i < rows; i++) {
                MapLinkProfile profile = profiles.get(i);
                MapLinkProfileState state = states.get(profile.id());
                float ry = rowY + i * ROW_H;
                float rw = Math.max(1.0f, width - 8.0f);
                boolean hover = inside(mouseX, mouseY, x + 4.0f, ry, rw, ROW_H - 4.0f);
                float hoverAnim = AnimationUtility.approach(hoverAnims.getOrDefault(profile.id(), 0.0f), hover ? 1.0f : 0.0f,
                        AnimationUtility.deltaTime(), hover ? 13.0f : 8.0f);
                hoverAnims.put(profile.id(), hoverAnim);
                boolean selected = profile.id().equals(selectedMapLinkProfileId);
                int bg = selected
                        ? SettingsGuiPalette.withAlpha(Theme.theme().accent(), Math.round(30.0f + hoverAnim * 12.0f))
                        : SettingsGuiPalette.withAlpha(palette.panelText(), Math.round(7.0f + hoverAnim * 11.0f));
                ClickGuiRenderer.drawRoundedRect(x + 4.0f, ry, rw, ROW_H - 4.0f, 9.0f, bg);
                if (selected) {
                    ClickGuiRenderer.drawRect(x + 4.0f, ry + 10.0f, 2.5f, ROW_H - 24.0f,
                            SettingsGuiPalette.withAlpha(Theme.theme().accent(), 230));
                }

                int statusColor = mapLinkStatusColor(state, profile.enabled());
                String name = profile.displayName().isBlank() ? profile.id() : profile.displayName();
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), name,
                        x + 16.0f, ry + 8.0f, 16.0f, profile.enabled() ? palette.panelText() : palette.panelMuted(), false);

                String provider = mapLinkProviderLabel(profile.providerType());
                String status = mapLinkStatusLabel(state == null ? (profile.enabled() ? MapLinkProfileStatus.IDLE : MapLinkProfileStatus.DISABLED) : state.status());
                int players = state == null ? 0 : state.playerCount();
                String meta = profile.serverMatcher() + "  ·  " + provider + "  ·  " + status + "  ·  " + players + " "
                        + tr("gui.combatant.map.maplink.profiles.players", "players");
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), meta,
                        x + 16.0f, ry + 29.0f, 12.3f, palette.panelMuted(), false);

                float toggleW = 54.0f;
                float toggleX = x + width - toggleW - 14.0f;
                float toggleY = ry + 12.0f;
                boolean toggleHover = inside(mouseX, mouseY, toggleX, toggleY, toggleW, 25.0f);
                ClickGuiRenderer.drawRoundedRect(toggleX, toggleY, toggleW, 25.0f, 7.0f,
                        profile.enabled() ? SettingsGuiPalette.withAlpha(statusColor, toggleHover ? 48 : 32)
                                : SettingsGuiPalette.withAlpha(palette.panelText(), toggleHover ? 18 : 9));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        profile.enabled() ? tr("gui.combatant.map.common.on", "On") : tr("gui.combatant.map.common.off", "Off"),
                        toggleX + 16.0f, toggleY + 6.0f, 12.5f,
                        profile.enabled() ? statusColor : palette.panelMuted(), false);
                rowHits.add(new MapLinkHit(profile.id(), x + 4.0f, ry, rw, ROW_H - 4.0f, toggleX, toggleY, toggleW, 25.0f));
            }

            MapLinkProfile selected = selectedMapLinkProfile();
            deleteW = 116.0f;
            deleteH = 28.0f;
            deleteX = x + width - deleteW - 6.0f;
            deleteY = rowY + rows * ROW_H + 2.0f;
            if (selected != null) {
                boolean hoverDelete = inside(mouseX, mouseY, deleteX, deleteY, deleteW, deleteH);
                ClickGuiRenderer.drawRoundedRect(deleteX, deleteY, deleteW, deleteH, 7.0f,
                        SettingsGuiPalette.withAlpha(0xFFFF6D78, hoverDelete ? 34 : 18));
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(), tr("gui.combatant.map.maplink.profiles.delete", "Delete profile"),
                        deleteX + 12.0f, deleteY + 7.0f, 12.5f, 0xFFFF818A, false);
            }
            if (profiles.size() > rows) {
                ClickGuiRenderer.drawText(ClickGuiRenderer.getOnestMedium(),
                        "+" + (profiles.size() - rows) + " " + tr("gui.combatant.map.common.more_search", "more · use search to narrow"),
                        x + 10.0f, rowY + rows * ROW_H + 8.0f, 12.0f, palette.panelMuted(), false);
            }
        }

        @Override
        public void mouseClicked(double mx, double my, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
            if (inside((float) mx, (float) my, createX, createY, createW, createH) && !currentServerAddress().isBlank()) {
                createMapLinkProfileForCurrentServer();
                return;
            }
            if (hasSelectedMapLinkProfile() && inside((float) mx, (float) my, deleteX, deleteY, deleteW, deleteH)) {
                deleteSelectedMapLinkProfile();
                return;
            }
            for (MapLinkHit hit : rowHits) {
                if (!inside((float) mx, (float) my, hit.x, hit.y, hit.w, hit.h)) continue;
                if (inside((float) mx, (float) my, hit.toggleX, hit.toggleY, hit.toggleW, hit.toggleH)) {
                    toggleMapLinkProfile(hit.profileId);
                } else {
                    selectMapLinkProfile(hit.profileId);
                }
                return;
            }
        }

        @Override
        public float getHeight() {
            List<MapLinkProfile> profiles = displayMapLinkProfiles();
            int rows = Math.min(MAX_ROWS, profiles.size());
            if (profiles.isEmpty()) return 136.0f;
            float footer = hasSelectedMapLinkProfile() || profiles.size() > rows ? 38.0f : 4.0f;
            return 60.0f + rows * ROW_H + footer;
        }

        @Override
        public String dynamicSearchText() {
            StringBuilder out = new StringBuilder();
            for (MapLinkProfile profile : MapLinkConfig.get().allProfiles()) out.append(' ').append(mapLinkSearchText(profile));
            return out.toString().toLowerCase(Locale.ROOT);
        }

        private List<MapLinkProfile> displayMapLinkProfiles() {
            List<MapLinkProfile> profiles = new ArrayList<>(MapLinkConfig.get().allProfiles());
            if (!selectedMapLinkProfileId.isBlank() && profiles.stream().noneMatch(p -> p.id().equals(selectedMapLinkProfileId))) {
                selectedMapLinkProfileId = "";
            }
            if (selectedMapLinkProfileId.isBlank() && !profiles.isEmpty()) selectedMapLinkProfileId = profiles.getFirst().id();
            profiles.sort(Comparator.comparing((MapLinkProfile p) -> !p.id().equals(selectedMapLinkProfileId))
                    .thenComparing((MapLinkProfile p) -> !p.enabled())
                    .thenComparing(Comparator.comparingInt(MapLinkProfile::sourcePriority).reversed())
                    .thenComparing(MapLinkProfile::id));
            String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || "maplink providers profiles status live stale players worlds server".contains(needle)) return profiles;
            return profiles.stream().filter(profile -> mapLinkSearchText(profile).contains(needle)).toList();
        }

        private static String mapLinkSearchText(MapLinkProfile profile) {
            return (profile.id() + " " + profile.displayName() + " " + profile.providerType() + " "
                    + profile.serverMatcher() + " " + profile.baseUrl()).toLowerCase(Locale.ROOT);
        }

        private void toggleMapLinkProfile(String id) {
            MapLinkConfig config = MapLinkConfig.get();
            List<MapLinkProfile> next = new ArrayList<>(config.allProfiles().size());
            boolean changed = false;
            for (MapLinkProfile profile : config.allProfiles()) {
                if (!profile.id().equals(id)) {
                    next.add(profile);
                    continue;
                }
                next.add(copyMapLinkProfile(profile, profile.displayName(), !profile.enabled(), profile.serverMatcher(),
                        profile.baseUrl(), profile.providerType(), profile.refreshIntervalMs(), profile.defaultY(),
                        profile.sourcePriority(), profile.dimensionMappings(), profile.requestHeaders()));
                changed = true;
            }
            if (changed) config.setProfiles(next);
        }

        private static int mapLinkStatusColor(MapLinkProfileState state, boolean enabled) {
            if (!enabled) return 0xFF858A94;
            if (state == null) return 0xFF9AA6B6;
            return switch (state.status()) {
                case LIVE -> 0xFF72E69A;
                case CONNECTING -> 0xFFFFD36A;
                case STALE, WORLD_UNMAPPED -> 0xFFFFA35C;
                default -> 0xFFFF6D78;
            };
        }

        private record MapLinkHit(String profileId, float x, float y, float w, float h,
                                  float toggleX, float toggleY, float toggleW, float toggleH) {}
    }

    private static String currentServerAddress() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getCurrentServer() == null || mc.getCurrentServer().ip == null) return "";
        return mc.getCurrentServer().ip.trim();
    }

    private static final class ExternalLongValue extends NumberValue<Long> {
        private final Supplier<Long> getter;
        private final Consumer<Long> setter;
        private ExternalLongValue(String name, Supplier<Long> getter, Consumer<Long> setter, long min, long max) {
            super(name, getter.get(), min, max);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Long get() { return getter.get(); }
        @Override public void set(Long value) { setter.accept(Math.max(getMin(), Math.min(getMax(), value == null ? getMin() : value))); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number n) set(n.longValue()); }
    }

    private static final class ExternalIntegerRangeValue extends NumberValue<Integer> {
        private final Supplier<Integer> getter;
        private final Consumer<Integer> setter;
        private ExternalIntegerRangeValue(String name, Supplier<Integer> getter, Consumer<Integer> setter, int min, int max) {
            super(name, getter.get(), min, max);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Integer get() { return getter.get(); }
        @Override public void set(Integer value) { setter.accept(Math.max(getMin(), Math.min(getMax(), value == null ? getMin() : value))); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number n) set(n.intValue()); }
    }

    private static final class ExternalMapSetValue extends SetValue {
        private final Supplier<Map<String, String>> getter;
        private final Consumer<Map<String, String>> setter;
        private ExternalMapSetValue(String name, Supplier<Map<String, String>> getter, Consumer<Map<String, String>> setter) {
            super(name);
            this.getter = getter;
            this.setter = setter;
        }
        @Override public Set<String> get() {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            Map<String, String> map = getter.get();
            if (map != null) map.forEach((key, value) -> out.add(key + "=" + value));
            return out;
        }
        @Override public void set(Set<String> rows) {
            LinkedHashMap<String, String> out = new LinkedHashMap<>();
            if (rows != null) {
                for (String row : rows) {
                    if (row == null) continue;
                    int split = row.indexOf('=');
                    if (split <= 0 || split >= row.length() - 1) continue;
                    String key = row.substring(0, split).trim();
                    String value = row.substring(split + 1).trim();
                    if (!key.isBlank() && !value.isBlank()) out.put(key, value);
                }
            }
            setter.accept(out);
        }
        @Override public Object toJson() { return new ArrayList<>(get()); }
        @Override public void fromJson(Object json) {
            if (!(json instanceof List<?> list)) return;
            LinkedHashSet<String> rows = new LinkedHashSet<>();
            for (Object item : list) if (item instanceof String text) rows.add(text);
            set(rows);
        }
    }

    private static final class ExternalBooleanValue extends BooleanValue {
        private final Supplier<Boolean> getter; private final Consumer<Boolean> setter;
        private ExternalBooleanValue(String name, Supplier<Boolean> getter, Consumer<Boolean> setter) { super(name, getter.get()); this.getter = getter; this.setter = setter; }
        @Override public Boolean get() { return getter.get(); }
        @Override public void set(Boolean value) { setter.accept(value); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Boolean value) set(value); }
    }
    private static final class ExternalIntegerValue extends NumberValue<Integer> {
        private final Supplier<Integer> getter; private final Consumer<Integer> setter; private final List<Integer> values; private final ConfigOption<Integer> option;
        private ExternalIntegerValue(String name, Supplier<Integer> getter, Consumer<Integer> setter, List<Integer> values, ConfigOption<Integer> option) { super(name, getter.get(), values.getFirst(), values.getLast()); this.getter = getter; this.setter = setter; this.values = values; this.option = option; }
        @Override public Integer get() { return getter.get(); }
        @Override public void set(Integer value) { setter.accept(nearest(value)); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number value) set(value.intValue()); }
        @Override public String toDisplay() { var display = option.getDisplayGetter().apply(option, get()); return display == null ? super.toDisplay() : LegacyTextUtil.stripLegacy(display.getString()); }
        private int nearest(int value) { int result = values.getFirst(); int distance = Math.abs(result - value); for (int candidate : values) { int d = Math.abs(candidate - value); if (d < distance) { result = candidate; distance = d; } } return result; }
    }
    private static final class ExternalDoubleValue extends NumberValue<Double> {
        private final Supplier<Double> getter; private final Consumer<Double> setter; private final List<Double> values; private final ConfigOption<Double> option;
        private ExternalDoubleValue(String name, Supplier<Double> getter, Consumer<Double> setter, List<Double> values, ConfigOption<Double> option) { super(name, getter.get(), values.getFirst(), values.getLast()); this.getter = getter; this.setter = setter; this.values = values; this.option = option; }
        @Override public Double get() { return getter.get(); }
        @Override public void set(Double value) { setter.accept(nearest(value)); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof Number value) set(value.doubleValue()); }
        @Override public String toDisplay() { var display = option.getDisplayGetter().apply(option, get()); return display == null ? super.toDisplay() : LegacyTextUtil.stripLegacy(display.getString()); }
        private double nearest(double value) { double result = values.getFirst(); double distance = Math.abs(result - value); for (double candidate : values) { double d = Math.abs(candidate - value); if (d < distance) { result = candidate; distance = d; } } return result; }
    }
    private static final class ExternalStringValue extends StringValue {
        private final Supplier<String> getter; private final Consumer<String> setter;
        private ExternalStringValue(String name, Supplier<String> getter, Consumer<String> setter) { super(name, getter.get()); this.getter = getter; this.setter = setter; }
        @Override public String get() { return getter.get(); }
        @Override public void set(String value) { setter.accept(value); }
        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof String value) set(value); }
    }
    private static final class ExternalModeValue<T> extends ModeValue {
        private final Supplier<T> getter;
        private final Consumer<T> setter;
        private final List<T> values;
        private final List<String> labels;

        private ExternalModeValue(String name, Supplier<T> getter, Consumer<T> setter,
                                  List<T> values, List<String> labels) {
            super(name, currentLabel(getter, values, labels), labels.toArray(String[]::new));
            this.getter = getter;
            this.setter = setter;
            this.values = List.copyOf(values);
            this.labels = List.copyOf(labels);
        }

        @Override public String get() {
            T current = getter.get();
            for (int i = 0; i < values.size(); i++) {
                if (Objects.equals(values.get(i), current)) return labels.get(i);
            }
            return labels.getFirst();
        }

        @Override public void set(String label) {
            int index = labels.indexOf(label);
            if (index >= 0) setter.accept(values.get(index));
        }

        @Override public Object toJson() { return get(); }
        @Override public void fromJson(Object json) { if (json instanceof String label) set(label); }

        private static <T> String currentLabel(Supplier<T> getter, List<T> values, List<String> labels) {
            T current = getter.get();
            for (int i = 0; i < values.size(); i++) {
                if (Objects.equals(values.get(i), current)) return labels.get(i);
            }
            return labels.getFirst();
        }
    }
}
