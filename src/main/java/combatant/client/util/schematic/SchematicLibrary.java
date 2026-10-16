/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.schematic;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * The {@code schematics/} folder in the game directory. Scanning and parsing run on one daemon worker so a
 * 50 MB schematic never stalls the render thread; headers are cached by path and modification time.
 */
public final class SchematicLibrary {

    public static final SchematicLibrary INSTANCE = new SchematicLibrary();
    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant/Schematic");

    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Combatant-Schematic");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final Map<Path, SchematicHeader> headerCache = new ConcurrentHashMap<>();
    private volatile List<SchematicHeader> headers = List.of();
    private volatile boolean scanning;
    private volatile int scanGeneration;
    private CompletableFuture<Void> pendingScan;

    private SchematicLibrary() {
    }

    public Path directory() {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.warn("Cannot create {}", dir, e);
        }
        return dir;
    }

    public List<SchematicHeader> headers() {
        return headers;
    }

    public boolean isScanning() {
        return scanning;
    }

    /** Bumped after every finished scan so views know to re-filter. */
    public int scanGeneration() {
        return scanGeneration;
    }

    public synchronized CompletableFuture<Void> refresh() {
        if (pendingScan != null && !pendingScan.isDone()) return pendingScan;
        scanning = true;
        pendingScan = CompletableFuture.runAsync(this::scan, worker).whenComplete((ignored, error) -> {
            scanning = false;
            if (error != null) LOGGER.warn("Schematic scan failed", error);
        });
        return pendingScan;
    }

    public CompletableFuture<Loaded> load(Path path) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Schematic schematic = SchematicFiles.read(path);
                return new Loaded(schematic, LayerStats.compute(schematic));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, worker);
    }

    /** Finds a file by name with or without the extension; falls back to a prefix match. */
    public Path resolve(String name) {
        if (name == null || name.isBlank()) return null;
        String wanted = name.trim();
        Path dir = directory();
        String lower = wanted.toLowerCase(Locale.ROOT);
        if (SchematicFiles.extensionOf(lower) != null) lower = SchematicFiles.baseName(lower);
        try {
            Path typed = dir.resolve(wanted);
            if (SchematicFiles.isSupported(typed) && Files.isRegularFile(typed) && typed.normalize().startsWith(dir.normalize())) return typed;
            for (String extension : SchematicFiles.EXTENSIONS) {
                Path direct = dir.resolve(wanted + extension);
                if (Files.isRegularFile(direct) && direct.normalize().startsWith(dir.normalize())) return direct;
            }
        } catch (java.nio.file.InvalidPathException ignored) {
            // Fall through to the prefix match; chat input can hold characters the file system rejects.
        }

        for (Path candidate : listFiles(dir)) {
            if (SchematicFiles.baseName(candidate).toLowerCase(Locale.ROOT).startsWith(lower)) return candidate;
        }
        return null;
    }

    public List<String> fileNames() {
        List<String> names = new ArrayList<>();
        for (Path path : listFiles(directory())) names.add(SchematicFiles.baseName(path));
        return names;
    }

    private void scan() {
        Path dir = directory();
        List<Path> files = listFiles(dir);
        List<SchematicHeader> next = new ArrayList<>(files.size());
        for (Path path : files) {
            next.add(header(path));
        }
        headerCache.keySet().retainAll(files);
        next.sort(Comparator.comparing(h -> h.fileName().toLowerCase(Locale.ROOT)));
        headers = List.copyOf(next);
        scanGeneration++;
    }

    private SchematicHeader header(Path path) {
        long modified;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            modified = 0L;
        }
        SchematicHeader cached = headerCache.get(path);
        if (cached != null && cached.lastModified() == modified) return cached;
        SchematicHeader header;
        try {
            header = SchematicFiles.readHeader(path, modified);
        } catch (IOException | RuntimeException e) {
            header = SchematicHeader.failed(path, modified, e.getMessage());
        }
        headerCache.put(path, header);
        return header;
    }

    private static List<Path> listFiles(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(SchematicFiles::isSupported)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public record Loaded(Schematic schematic, LayerStats stats) {
    }
}
