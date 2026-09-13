/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** RIFF/WAVE PCM16 decoder used internally by the format-independent sound system. */
final class WavAudioDecoder implements AudioDecoder {
    private static final int PCM_ENCODING = 0x0001;
    private static final int EXTENSIBLE_ENCODING = 0xfffe;
    private static final byte[] PCM_SUBFORMAT_GUID = {
            0x01, 0x00, 0x00, 0x00,
            0x00, 0x00,
            0x10, 0x00,
            (byte) 0x80, 0x00,
            0x00, (byte) 0xaa, 0x00, 0x38, (byte) 0x9b, 0x71
    };

    @Override
    public PcmAudioData decode(InputStream input) throws IOException {
        byte[] bytes = input.readAllBytes();
        if (bytes.length < 44) throw new IOException("Invalid WAV: header too short");
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(0) != 0x46464952) throw new IOException("Invalid WAV: missing RIFF");
        if (buffer.getInt(8) != 0x45564157) throw new IOException("Invalid WAV: missing WAVE");

        Integer encoding = null;
        Integer channels = null;
        Integer sampleRate = null;
        Integer bitsPerSample = null;
        byte[] pcm = null;
        int offset = 12;
        while (offset <= bytes.length - 8) {
            int chunkId = buffer.getInt(offset);
            long unsignedSize = Integer.toUnsignedLong(buffer.getInt(offset + 4));
            long dataStart = (long) offset + 8L;
            long dataEnd = dataStart + unsignedSize;
            if (dataEnd > bytes.length) throw new IOException("Invalid WAV: truncated chunk");

            if (chunkId == 0x20746D66) {
                if (unsignedSize < 16L) throw new IOException("Invalid WAV: fmt chunk too small");
                int start = (int) dataStart;
                int declaredEncoding = buffer.getShort(start) & 0xffff;
                channels = buffer.getShort(start + 2) & 0xffff;
                sampleRate = buffer.getInt(start + 4);
                bitsPerSample = buffer.getShort(start + 14) & 0xffff;
                encoding = declaredEncoding == EXTENSIBLE_ENCODING
                        ? extensibleEncoding(buffer, start, unsignedSize, bitsPerSample)
                        : declaredEncoding;
            } else if (chunkId == 0x61746164 && unsignedSize > 0L) {
                pcm = Arrays.copyOfRange(bytes, (int) dataStart, (int) dataEnd);
            }

            long next = dataEnd + (unsignedSize & 1L);
            if (next > Integer.MAX_VALUE || next <= offset) throw new IOException("Invalid WAV: chunk overflow");
            offset = (int) next;
        }

        if (encoding == null || channels == null || sampleRate == null || bitsPerSample == null) {
            throw new IOException("Invalid WAV: missing fmt chunk");
        }
        if (pcm == null || pcm.length == 0) throw new IOException("Invalid WAV: missing data chunk");
        if (encoding != PCM_ENCODING) {
            throw new IOException("Unsupported WAV encoding: " + encoding + " (PCM required)");
        }
        if (bitsPerSample != 16) throw new IOException("Unsupported WAV bit depth: " + bitsPerSample);
        if (channels != 1 && channels != 2) throw new IOException("Unsupported WAV channel count: " + channels);
        return new PcmAudioData(channels, sampleRate, pcm);
    }

    private static int extensibleEncoding(ByteBuffer buffer,
                                          int fmtStart,
                                          long fmtSize,
                                          int bitsPerSample) throws IOException {
        if (fmtSize < 40L) throw new IOException("Invalid extensible WAV: fmt chunk too small");
        int extensionSize = buffer.getShort(fmtStart + 16) & 0xffff;
        if (extensionSize < 22) throw new IOException("Invalid extensible WAV: extension too small");

        int validBits = buffer.getShort(fmtStart + 18) & 0xffff;
        if (validBits != 0 && validBits != bitsPerSample) {
            throw new IOException("Unsupported extensible WAV valid bit depth: " + validBits);
        }
        for (int i = 0; i < PCM_SUBFORMAT_GUID.length; i++) {
            if (buffer.get(fmtStart + 24 + i) != PCM_SUBFORMAT_GUID[i]) {
                throw new IOException("Unsupported extensible WAV subformat (PCM required)");
            }
        }
        return PCM_ENCODING;
    }
}
