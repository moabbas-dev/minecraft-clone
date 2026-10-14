package com.example.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A skin that moved between Minecraft versions is found under whichever name exists. */
class EntityTextureTest {
    @TempDir
    Path dir;

    private String touch(String name) throws IOException {
        Path p = dir.resolve(name);
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[]{1});
        return p.toString();
    }

    @Test
    void theNewestExistingNameWins() throws IOException {
        String newer = touch("entity/pig/temperate_pig.png");
        String older = touch("entity/pig/pig.png");
        assertEquals(newer, EntityTexture.firstExisting(newer, older));
    }

    @Test
    void anOlderNameIsUsedWhenTheNewerOneIsMissing() throws IOException {
        String newer = dir.resolve("entity/cow/temperate_cow.png").toString();
        String older = touch("entity/cow/cow.png");
        assertEquals(older, EntityTexture.firstExisting(newer, older));
    }

    @Test
    void withNothingOnDiskTheFirstCandidateIsReportedAsTheMissingOne() {
        String a = dir.resolve("a.png").toString(), b = dir.resolve("b.png").toString();
        assertEquals(a, EntityTexture.firstExisting(a, b));
    }
}
