package com.example.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundGroupTest {

    @Test
    void validGroupConstructsFine() {
        SoundGroup g = new SoundGroup("test.id", new String[]{"a.ogg"}, AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 16f);
        assertEquals("test.id", g.id());
        assertEquals(1, g.fileNames().length);
    }

    @Test
    void rejectsEmptyFileList() {
        assertThrows(IllegalArgumentException.class, () ->
                new SoundGroup("test.id", new String[0], AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 16f));
    }

    @Test
    void rejectsNullFileList() {
        assertThrows(IllegalArgumentException.class, () ->
                new SoundGroup("test.id", null, AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 16f));
    }

    @Test
    void rejectsVolumeAboveOne() {
        assertThrows(IllegalArgumentException.class, () ->
                new SoundGroup("test.id", new String[]{"a.ogg"}, AudioCategory.BLOCKS, 1.5f, 0.9f, 1.1f, true, 16f));
    }

    @Test
    void rejectsNegativeVolume() {
        assertThrows(IllegalArgumentException.class, () ->
                new SoundGroup("test.id", new String[]{"a.ogg"}, AudioCategory.BLOCKS, -0.1f, 0.9f, 1.1f, true, 16f));
    }

    @Test
    void rejectsZeroOrNegativePitchMin() {
        assertThrows(IllegalArgumentException.class, () ->
                new SoundGroup("test.id", new String[]{"a.ogg"}, AudioCategory.BLOCKS, 0.8f, 0f, 1.1f, true, 16f));
    }

    @Test
    void rejectsPitchMaxBelowPitchMin() {
        assertThrows(IllegalArgumentException.class, () ->
                new SoundGroup("test.id", new String[]{"a.ogg"}, AudioCategory.BLOCKS, 0.8f, 1.1f, 0.9f, true, 16f));
    }

    @Test
    void allowsEqualPitchMinAndMax() {
        assertDoesNotThrow(() ->
                new SoundGroup("test.id", new String[]{"a.ogg"}, AudioCategory.BLOCKS, 0.8f, 1f, 1f, false, 0f));
    }
}
