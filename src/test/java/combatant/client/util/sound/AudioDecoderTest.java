/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class AudioDecoderTest {
    @Test
    void decodesPcm16Wav() throws Exception {
        byte[] samples = {1, 0, -1, 127};
        PcmAudioData decoded = new WavAudioDecoder().decode(new ByteArrayInputStream(wav(samples, 1, 22_050)));

        assertEquals(1, decoded.channels());
        assertEquals(22_050, decoded.sampleRate());
        assertArrayEquals(samples, decoded.data());
    }

    @Test
    void decodesExtensiblePcm16Wav() throws Exception {
        byte[] samples = {1, 0, -1, 127};
        PcmAudioData decoded = new WavAudioDecoder().decode(
                new ByteArrayInputStream(extensibleWav(samples, 2, 96_000))
        );

        assertEquals(2, decoded.channels());
        assertEquals(96_000, decoded.sampleRate());
        assertArrayEquals(samples, decoded.data());
    }

    @Test
    void decodesBundledGuiOpenAndCloseSounds() throws Exception {
        for (String resource : new String[]{
                "/assets/combatant/sounds/gui/gui_open.wav",
                "/assets/combatant/sounds/gui/gui_close.wav"
        }) {
            try (var input = AudioDecoderTest.class.getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                PcmAudioData decoded = new WavAudioDecoder().decode(input);
                assertEquals(2, decoded.channels(), resource);
                assertEquals(96_000, decoded.sampleRate(), resource);
            }
        }
    }

    @Test
    void spatialDownmixAveragesStereoFrames() {
        byte[] stereo = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 1_000).putShort((short) -1_000)
                .putShort((short) 20_000).putShort((short) 10_000)
                .array();

        PcmAudioData mono = new PcmAudioData(2, 48_000, stereo).toMono();
        assertEquals(1, mono.channels());
        assertArrayEquals(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0).putShort((short) 15_000).array(), mono.data());
    }

    private static byte[] wav(byte[] samples, int channels, int sampleRate) {
        int byteRate = sampleRate * channels * Short.BYTES;
        int blockAlign = channels * Short.BYTES;
        return ByteBuffer.allocate(44 + samples.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46464952)
                .putInt(36 + samples.length)
                .putInt(0x45564157)
                .putInt(0x20746d66)
                .putInt(16)
                .putShort((short) 1)
                .putShort((short) channels)
                .putInt(sampleRate)
                .putInt(byteRate)
                .putShort((short) blockAlign)
                .putShort((short) 16)
                .putInt(0x61746164)
                .putInt(samples.length)
                .put(samples)
                .array();
    }

    private static byte[] extensibleWav(byte[] samples, int channels, int sampleRate) {
        int byteRate = sampleRate * channels * Short.BYTES;
        int blockAlign = channels * Short.BYTES;
        return ByteBuffer.allocate(68 + samples.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46464952)
                .putInt(60 + samples.length)
                .putInt(0x45564157)
                .putInt(0x20746d66)
                .putInt(40)
                .putShort((short) 0xfffe)
                .putShort((short) channels)
                .putInt(sampleRate)
                .putInt(byteRate)
                .putShort((short) blockAlign)
                .putShort((short) 16)
                .putShort((short) 22)
                .putShort((short) 16)
                .putInt(0)
                .put(new byte[]{
                        0x01, 0x00, 0x00, 0x00,
                        0x00, 0x00,
                        0x10, 0x00,
                        (byte) 0x80, 0x00,
                        0x00, (byte) 0xaa, 0x00, 0x38, (byte) 0x9b, 0x71
                })
                .putInt(0x61746164)
                .putInt(samples.length)
                .put(samples)
                .array();
    }
}
