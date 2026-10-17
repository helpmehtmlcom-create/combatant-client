/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.nav;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import combatant.client.config.ConfigPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent ClickGUI navigation state: pinned modules, recently used modules, the module whose
 * settings were open at close, and per-panel scroll / subcategory.
 */
public final class ClickGuiMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ClickGuiMemory INSTANCE = new ClickGuiMemory(ConfigPaths.root().resolve("clickgui_state.json"));

    private final Path file;
    private final Set<String> favourites = new LinkedHashSet<>();
    private final RecentList recents = new RecentList(8);
    private String lastModule;
    private final Map<String, PanelState> panels = new HashMap<>();
    private boolean loaded;

    public static final class PanelState {
        public float scroll;
        public String subcategory;
    }

    private static final class Disk {
        List<String> favourites = new ArrayList<>();
        List<String> recents = new ArrayList<>();
        String lastModule;
        Map<String, PanelState> panels = new HashMap<>();
    }

    public ClickGuiMemory(Path file) {
        this.file = file;
    }

    public static ClickGuiMemory get() {
        INSTANCE.ensureLoaded();
        return INSTANCE;
    }

    public synchronized boolean isFavourite(String id) {
        return id != null && favourites.contains(id);
    }

    public synchronized boolean toggleFavourite(String id) {
        if (id == null || id.isBlank()) return false;
        boolean now = favourites.add(id);
        if (!now) favourites.remove(id);
        save();
        return now;
    }

    public synchronized void markUsed(String id) {
        recents.touch(id);
        save();
    }

    /** 0 = most recent; -1 = not recent. */
    public synchronized int recentIndex(String id) {
        return recents.indexOf(id);
    }

    public synchronized boolean hasQuickList() {
        return !favourites.isEmpty() || !recents.snapshot().isEmpty();
    }

    public synchronized String lastModule() {
        return lastModule;
    }

    public synchronized void setLastModule(String id) {
        this.lastModule = id;
        save();
    }

    public synchronized PanelState panel(String category) {
        return panels.computeIfAbsent(category, k -> new PanelState());
    }

    public synchronized void savePanels() {
        save();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(file)) return;
        try {
            Disk disk = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Disk.class);
            if (disk == null) return;
            if (disk.favourites != null) favourites.addAll(disk.favourites);
            recents.replaceAll(disk.recents);
            lastModule = disk.lastModule;
            if (disk.panels != null) panels.putAll(disk.panels);
        } catch (IOException | RuntimeException ignored) {
            // Corrupt or unreadable state is not worth failing the GUI over; start fresh.
        }
    }

    private void save() {
        Disk disk = new Disk();
        disk.favourites = new ArrayList<>(favourites);
        disk.recents = recents.snapshot();
        disk.lastModule = lastModule;
        disk.panels = panels;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(disk), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Best effort persistence.
        }
    }
}
