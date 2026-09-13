/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/** Rebuildable global history catalog. No chunk payload is decompressed while rebuilding. */
public final class MapHistoryCatalog {
    private static final int MAGIC = 0x43425049; // CBPI
    private static final int VERSION = 1;
    private static final int MAX_STRING_BYTES = 32 * 1024;
    private static final int MAX_ENTRIES = 5_000_000;

    private final Path root;
    private final Path file;
    private final List<MapHistoryCatalogEntry> entries = new ArrayList<>();

    public MapHistoryCatalog(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.file = this.root.resolve("history.cbpidx");
    }

    public synchronized void loadOrRebuild() throws IOException {
        Files.createDirectories(root);
        try {
            load();
        } catch (IOException broken) {
            rebuild();
        }
    }

    public synchronized List<MapHistoryCatalogEntry> snapshot() {
        return List.copyOf(entries);
    }

    public synchronized List<MapHistoryCatalogEntry> search(String query, int limit) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int boundedLimit = Math.max(1, Math.min(10_000, limit));
        Map<String, MapHistoryCatalogEntry> latest = new LinkedHashMap<>();
        for (MapHistoryCatalogEntry entry : entries) {
            String uuid = entry.targetUuid().toString().toLowerCase(Locale.ROOT);
            String name = entry.targetName() == null ? "" : entry.targetName().toLowerCase(Locale.ROOT);
            if (!needle.isEmpty() && !uuid.contains(needle) && !name.contains(needle)) continue;
            String key = entry.serverKey() + "\n" + entry.targetUuid();
            MapHistoryCatalogEntry previous = latest.get(key);
            if (previous == null || entry.endMs() > previous.endMs()) latest.put(key, entry);
        }
        return latest.values().stream()
                .sorted(Comparator.comparingLong(MapHistoryCatalogEntry::endMs).reversed())
                .limit(boundedLimit)
                .toList();
    }

    public synchronized List<Path> filesFor(MapHistoryQuery query) {
        if (query == null) return List.of();
        return entries.stream()
                .filter(entry -> entry.targetUuid().equals(query.targetUuid()))
                .filter(entry -> query.serverKey().isBlank() || query.serverKey().equals(entry.serverKey()))
                .filter(entry -> query.worlds().isEmpty() || query.worlds().contains(entry.worldKey()))
                .filter(entry -> query.sources().isEmpty() || query.sources().contains(entry.source()))
                .filter(entry -> query.includeBearings() || entry.type() != MapHistoryRecordType.BEARING)
                .filter(entry -> entry.endMs() >= query.fromMs() && entry.startMs() <= query.toMs())
                .map(MapHistoryCatalogEntry::file)
                .distinct()
                .toList();
    }

    public synchronized void add(Path dataFile, Collection<MapHistoryChunkMeta> chunks) throws IOException {
        if (dataFile == null || chunks == null || chunks.isEmpty()) return;
        Path absolute = dataFile.toAbsolutePath().normalize();
        for (MapHistoryChunkMeta meta : chunks) {
            entries.add(new MapHistoryCatalogEntry(absolute, meta.serverKey(), meta.targetUuid(),
                    meta.targetName(), meta.worldKey(), meta.source(), meta.type(),
                    meta.startMs(), meta.endMs(), meta.recordCount()));
        }
        compactInMemory();
        save();
    }

    public synchronized void removeMissingFiles() throws IOException {
        entries.removeIf(entry -> !Files.isRegularFile(entry.file()));
        compactInMemory();
        save();
    }

    public synchronized void rebuild() throws IOException {
        Files.createDirectories(root);
        List<MapHistoryCatalogEntry> rebuilt = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path candidate : stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".cbpdat")).toList()) {
                List<MapHistoryChunkMeta> chunks;
                try {
                    chunks = CbpDatFile.inspect(candidate);
                } catch (IOException ignored) {
                    continue;
                }
                Path absolute = candidate.toAbsolutePath().normalize();
                for (MapHistoryChunkMeta meta : chunks) {
                    rebuilt.add(new MapHistoryCatalogEntry(absolute, meta.serverKey(), meta.targetUuid(),
                            meta.targetName(), meta.worldKey(), meta.source(), meta.type(),
                            meta.startMs(), meta.endMs(), meta.recordCount()));
                }
            }
        }
        entries.clear();
        entries.addAll(rebuilt);
        compactInMemory();
        save();
    }

    private void load() throws IOException {
        if (!Files.isRegularFile(file)) {
            rebuild();
            return;
        }
        byte[] payload;
        int expectedCrc;
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) throw new IOException("CBPI magic");
            if (in.readInt() != VERSION) throw new IOException("CBPI version");
            int length = in.readInt();
            expectedCrc = in.readInt();
            if (length < 0 || length > 512 * 1024 * 1024) throw new IOException("CBPI length");
            payload = in.readNBytes(length);
            if (payload.length != length) throw new IOException("CBPI truncated");
        }
        CRC32 crc = new CRC32();
        crc.update(payload);
        if ((int) crc.getValue() != expectedCrc) throw new IOException("CBPI CRC");

        List<MapHistoryCatalogEntry> loaded = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(payload))) {
            int count = in.readInt();
            if (count < 0 || count > MAX_ENTRIES) throw new IOException("CBPI count");
            for (int i = 0; i < count; i++) {
                Path dataFile = root.resolve(readString(in)).normalize().toAbsolutePath();
                if (!dataFile.startsWith(root)) throw new IOException("CBPI path escapes history root");
                String server = readString(in);
                UUID target = new UUID(in.readLong(), in.readLong());
                String name = readString(in);
                String world = readString(in);
                MapHistorySource source = enumAt(MapHistorySource.values(), in.readInt());
                MapHistoryRecordType type = enumAt(MapHistoryRecordType.values(), in.readInt());
                long start = in.readLong();
                long end = in.readLong();
                int records = in.readInt();
                loaded.add(new MapHistoryCatalogEntry(dataFile, server, target, name, world, source, type,
                        start, end, records));
            }
        }
        entries.clear();
        entries.addAll(loaded);
        compactInMemory();
    }

    private void save() throws IOException {
        Files.createDirectories(root);
        byte[] payload;
        try (java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(Math.max(256, entries.size() * 96));
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(entries.size());
            for (MapHistoryCatalogEntry entry : entries) {
                Path relative;
                try {
                    relative = root.relativize(entry.file().toAbsolutePath().normalize());
                } catch (IllegalArgumentException differentRoot) {
                    relative = entry.file().getFileName();
                }
                writeString(out, relative.toString().replace('\\', '/'));
                writeString(out, entry.serverKey());
                out.writeLong(entry.targetUuid().getMostSignificantBits());
                out.writeLong(entry.targetUuid().getLeastSignificantBits());
                writeString(out, entry.targetName());
                writeString(out, entry.worldKey());
                out.writeInt(entry.source().ordinal());
                out.writeInt(entry.type().ordinal());
                out.writeLong(entry.startMs());
                out.writeLong(entry.endMs());
                out.writeInt(entry.recordCount());
            }
            out.flush();
            payload = bytes.toByteArray();
        }

        CRC32 crc = new CRC32();
        crc.update(payload);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(payload.length);
            out.writeInt((int) crc.getValue());
            out.write(payload);
            out.flush();
        }
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void compactInMemory() {
        entries.removeIf(entry -> entry == null || entry.targetUuid() == null || entry.file() == null);
        entries.sort(Comparator
                .comparing((MapHistoryCatalogEntry e) -> e.file().toString())
                .thenComparing(MapHistoryCatalogEntry::startMs)
                .thenComparing(e -> e.targetUuid().toString())
                .thenComparing(e -> e.source().ordinal())
                .thenComparing(e -> e.type().ordinal()));
        if (entries.size() < 2) return;
        List<MapHistoryCatalogEntry> compact = new ArrayList<>(entries.size());
        MapHistoryCatalogEntry last = null;
        for (MapHistoryCatalogEntry entry : entries) {
            if (last != null && sameChunkIdentity(last, entry)) continue;
            compact.add(entry);
            last = entry;
        }
        entries.clear();
        entries.addAll(compact);
    }

    private static boolean sameChunkIdentity(MapHistoryCatalogEntry a, MapHistoryCatalogEntry b) {
        return a.file().equals(b.file())
                && a.targetUuid().equals(b.targetUuid())
                && a.source() == b.source()
                && a.type() == b.type()
                && a.startMs() == b.startMs()
                && a.endMs() == b.endMs()
                && a.recordCount() == b.recordCount();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) throw new IOException("CBPI string too long");
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES) throw new IOException("CBPI string length");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("CBPI truncated string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static <E> E enumAt(E[] values, int ordinal) throws IOException {
        if (ordinal < 0 || ordinal >= values.length) throw new IOException("CBPI enum");
        return values[ordinal];
    }
}
