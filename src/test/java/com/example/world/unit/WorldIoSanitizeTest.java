package com.example.world.unit;

import com.example.world.WorldIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldIoSanitizeTest {

    @Test
    void normalNameIsUnchanged() {
        assertEquals("My World", WorldIO.sanitizeFolderName("My World"));
    }

    @Test
    void illegalWindowsFilenameCharactersAreReplaced() {
        String sanitized = WorldIO.sanitizeFolderName("a\\b/c:d*e?f\"g<h>i|j");
        assertFalse(sanitized.contains("\\"));
        assertFalse(sanitized.contains("/"));
        assertFalse(sanitized.contains(":"));
        assertFalse(sanitized.contains("*"));
        assertFalse(sanitized.contains("?"));
        assertFalse(sanitized.contains("\""));
        assertFalse(sanitized.contains("<"));
        assertFalse(sanitized.contains(">"));
        assertFalse(sanitized.contains("|"));
    }

    @Test
    void blankOrEmptyNameFallsBackToDefault() {
        assertEquals("World", WorldIO.sanitizeFolderName(""));
        assertEquals("World", WorldIO.sanitizeFolderName("   "));
    }

    @Test
    void surroundingWhitespaceIsTrimmed() {
        assertEquals("Trimmed", WorldIO.sanitizeFolderName("  Trimmed  "));
    }
}
