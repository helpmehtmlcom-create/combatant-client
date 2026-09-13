/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.map.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

final class CbpDatCodec {
    static final int CODEC_DEFLATE = 1;
    private static final int PAYLOAD_VERSION = 1;
    private static final double COORD_SCALE = 1000.0;

    private CbpDatCodec() {
    }

    static Encoded encode(List<? extends MapHistoryRecord> records) throws IOException {
        if (records == null || records.isEmpty()) throw new IllegalArgumentException("records");
        List<MapHistoryRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparingLong(MapHistoryRecord::observedAtMs));

        MapHistoryRecord first = ordered.getFirst();
        for (MapHistoryRecord record : ordered) {
            if (record == null
                    || record.type() != first.type()
                    || record.source() != first.source()
                    || !record.targetUuid().equals(first.targetUuid())
                    || !record.worldKey().equals(first.worldKey())
                    || !record.sourceKey().equals(first.sourceKey())) {
                throw new IllegalArgumentException("Chunk records must share identity/source/type");
            }
        }

        byte[] plain;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(256, ordered.size() * 32));
             DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(PAYLOAD_VERSION);
            out.writeInt(ordered.size());
            out.writeLong(first.observedAtMs());
            switch (first.type()) {
                case ESTIMATE -> writeEstimates(out, ordered);
                case EXACT_POINT -> writeExact(out, ordered);
                case BEARING -> writeBearings(out, ordered);
            }
            out.flush();
            plain = bytes.toByteArray();
        }

        CRC32 crc = new CRC32();
        crc.update(plain);

        byte[] compressed;
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(128, plain.length / 2));
             DeflaterOutputStream deflate = new DeflaterOutputStream(bytes, deflater, 8192, true)) {
            deflate.write(plain);
            deflate.finish();
            compressed = bytes.toByteArray();
        } finally {
            deflater.end();
        }

        return new Encoded(
                compressed,
                plain.length,
                (int) crc.getValue(),
                ordered.getFirst().observedAtMs(),
                ordered.getLast().observedAtMs(),
                ordered.size()
        );
    }

    static List<MapHistoryRecord> decode(MapHistoryChunkMeta meta, byte[] compressed) throws IOException {
        byte[] plain;
        try (InflaterInputStream inflate = new InflaterInputStream(new ByteArrayInputStream(compressed));
             ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(256, meta.uncompressedLength()))) {
            inflate.transferTo(bytes);
            plain = bytes.toByteArray();
        }

        if (plain.length != meta.uncompressedLength()) {
            throw new IOException("CBPD uncompressed length mismatch");
        }
        CRC32 crc = new CRC32();
        crc.update(plain);
        if ((int) crc.getValue() != meta.crc32()) {
            throw new IOException("CBPD chunk CRC mismatch");
        }

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(plain))) {
            int version = in.readInt();
            if (version != PAYLOAD_VERSION) throw new IOException("Unsupported CBPD payload " + version);
            int count = in.readInt();
            if (count < 0 || count > 1_000_000 || count != meta.recordCount()) {
                throw new IOException("Invalid CBPD record count");
            }
            long baseTime = in.readLong();
            return switch (meta.type()) {
                case ESTIMATE -> readEstimates(in, meta, count, baseTime);
                case EXACT_POINT -> readExact(in, meta, count, baseTime);
                case BEARING -> readBearings(in, meta, count, baseTime);
            };
        }
    }

    private static void writeEstimates(DataOutputStream out, List<MapHistoryRecord> records) throws IOException {
        long previousTime = records.getFirst().observedAtMs();
        long previousX = 0L, previousZ = 0L, previousRevision = 0L, previousSegment = 0L;
        for (MapHistoryRecord raw : records) {
            MapEstimateFrame frame = (MapEstimateFrame) raw;
            writeUnsignedVarLong(out, Math.max(0L, frame.observedAtMs() - previousTime));
            previousTime = frame.observedAtMs();
            long x = quantize(frame.x()), z = quantize(frame.z());
            writeSignedVarLong(out, x - previousX); previousX = x;
            writeSignedVarLong(out, z - previousZ); previousZ = z;
            out.writeFloat((float) frame.uncertaintyMajor());
            out.writeFloat((float) frame.uncertaintyMinor());
            out.writeFloat((float) frame.uncertaintyAngleRadians());
            out.writeFloat((float) frame.residualRms());
            out.writeFloat((float) frame.confidence());
            writeUnsignedVarLong(out, frame.sampleCount());
            writeUnsignedVarLong(out, frame.inlierCount());
            writeSignedVarLong(out, frame.segmentId() - previousSegment); previousSegment = frame.segmentId();
            writeSignedVarLong(out, frame.sourceRevision() - previousRevision); previousRevision = frame.sourceRevision();
        }
    }

    private static List<MapHistoryRecord> readEstimates(DataInputStream in, MapHistoryChunkMeta meta,
                                                        int count, long baseTime) throws IOException {
        List<MapHistoryRecord> out = new ArrayList<>(count);
        long time = baseTime, x = 0L, z = 0L, revision = 0L, segment = 0L;
        for (int i = 0; i < count; i++) {
            time += readUnsignedVarLong(in);
            x += readSignedVarLong(in);
            z += readSignedVarLong(in);
            double major = in.readFloat();
            double minor = in.readFloat();
            double angle = in.readFloat();
            double rms = in.readFloat();
            double confidence = in.readFloat();
            int samples = checkedInt(readUnsignedVarLong(in));
            int inliers = checkedInt(readUnsignedVarLong(in));
            segment += readSignedVarLong(in);
            revision += readSignedVarLong(in);
            out.add(new MapEstimateFrame(meta.targetUuid(), meta.targetName(), meta.worldKey(), meta.source(),
                    meta.sourceKey(), time, revision, dequantize(x), dequantize(z), major, minor, angle, rms,
                    confidence, samples, inliers, segment));
        }
        return List.copyOf(out);
    }

    private static void writeExact(DataOutputStream out, List<MapHistoryRecord> records) throws IOException {
        long previousTime = records.getFirst().observedAtMs();
        long previousX = 0L, previousY = 0L, previousZ = 0L, previousRevision = 0L;
        for (MapHistoryRecord raw : records) {
            MapExactPointFrame frame = (MapExactPointFrame) raw;
            writeUnsignedVarLong(out, Math.max(0L, frame.observedAtMs() - previousTime));
            previousTime = frame.observedAtMs();
            long x = quantize(frame.x()), y = quantize(frame.y()), z = quantize(frame.z());
            writeSignedVarLong(out, x - previousX); previousX = x;
            writeSignedVarLong(out, y - previousY); previousY = y;
            writeSignedVarLong(out, z - previousZ); previousZ = z;
            out.writeFloat((float) frame.accuracyRadius());
            writeSignedVarLong(out, frame.sourceRevision() - previousRevision); previousRevision = frame.sourceRevision();
        }
    }

    private static List<MapHistoryRecord> readExact(DataInputStream in, MapHistoryChunkMeta meta,
                                                    int count, long baseTime) throws IOException {
        List<MapHistoryRecord> out = new ArrayList<>(count);
        long time = baseTime, x = 0L, y = 0L, z = 0L, revision = 0L;
        for (int i = 0; i < count; i++) {
            time += readUnsignedVarLong(in);
            x += readSignedVarLong(in);
            y += readSignedVarLong(in);
            z += readSignedVarLong(in);
            double accuracy = in.readFloat();
            revision += readSignedVarLong(in);
            out.add(new MapExactPointFrame(meta.targetUuid(), meta.targetName(), meta.worldKey(), meta.source(),
                    meta.sourceKey(), time, revision, dequantize(x), dequantize(y), dequantize(z), accuracy));
        }
        return List.copyOf(out);
    }

    private static void writeBearings(DataOutputStream out, List<MapHistoryRecord> records) throws IOException {
        long previousTime = records.getFirst().observedAtMs();
        long previousX = 0L, previousZ = 0L, previousRevision = 0L;
        for (MapHistoryRecord raw : records) {
            MapBearingFrame frame = (MapBearingFrame) raw;
            writeUnsignedVarLong(out, Math.max(0L, frame.observedAtMs() - previousTime));
            previousTime = frame.observedAtMs();
            long x = quantize(frame.observerX()), z = quantize(frame.observerZ());
            writeSignedVarLong(out, x - previousX); previousX = x;
            writeSignedVarLong(out, z - previousZ); previousZ = z;
            out.writeFloat((float) frame.bearingRadians());
            out.writeFloat((float) frame.weight());
            writeSignedVarLong(out, frame.sourceRevision() - previousRevision); previousRevision = frame.sourceRevision();
        }
    }

    private static List<MapHistoryRecord> readBearings(DataInputStream in, MapHistoryChunkMeta meta,
                                                       int count, long baseTime) throws IOException {
        List<MapHistoryRecord> out = new ArrayList<>(count);
        long time = baseTime, x = 0L, z = 0L, revision = 0L;
        for (int i = 0; i < count; i++) {
            time += readUnsignedVarLong(in);
            x += readSignedVarLong(in);
            z += readSignedVarLong(in);
            double bearing = in.readFloat();
            double weight = in.readFloat();
            revision += readSignedVarLong(in);
            out.add(new MapBearingFrame(meta.targetUuid(), meta.targetName(), meta.worldKey(), meta.source(),
                    meta.sourceKey(), time, revision, dequantize(x), dequantize(z), bearing, weight));
        }
        return List.copyOf(out);
    }

    private static long quantize(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite coordinate");
        return Math.round(value * COORD_SCALE);
    }

    private static double dequantize(long value) {
        return value / COORD_SCALE;
    }

    private static int checkedInt(long value) throws IOException {
        if (value < 0 || value > Integer.MAX_VALUE) throw new IOException("varint overflow");
        return (int) value;
    }

    static void writeUnsignedVarLong(DataOutputStream out, long value) throws IOException {
        if (value < 0) throw new IllegalArgumentException("negative unsigned varlong");
        while ((value & ~0x7FL) != 0L) {
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((int) value);
    }

    static long readUnsignedVarLong(DataInputStream in) throws IOException {
        long value = 0L;
        int position = 0;
        while (position < 64) {
            int b = in.readUnsignedByte();
            value |= (long) (b & 0x7F) << position;
            if ((b & 0x80) == 0) return value;
            position += 7;
        }
        throw new IOException("varlong too long");
    }

    static void writeSignedVarLong(DataOutputStream out, long value) throws IOException {
        writeUnsignedVarLong(out, (value << 1) ^ (value >> 63));
    }

    static long readSignedVarLong(DataInputStream in) throws IOException {
        long raw = readUnsignedVarLong(in);
        return (raw >>> 1) ^ -(raw & 1L);
    }

    record Encoded(byte[] compressed, int uncompressedLength, int crc32, long startMs, long endMs, int count) {
    }
}
