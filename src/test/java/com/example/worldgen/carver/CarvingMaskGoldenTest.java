package com.example.worldgen.carver;

import com.example.worldgen.ChunkPos;
import com.example.worldgen.provider.WorldGenerationContext;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Carving masks against the oracle: the real 26.3 {@code CaveWorldCarver}, {@code CanyonWorldCarver},
 * {@code CarvingMask}, {@code WorldgenRandom}/{@code LegacyRandomSource} and value providers, driven by the
 * {@code generateCarvers} 17x17 loop, for 4 world seeds x 6 chunks (incl. negative coordinates and chunks reached by
 * canyons) with the overworld list {@code [cave, cave_extra_underground, canyon]} and with {@code [canyon]} alone.
 * Each golden record is the complete carved set as {@code CarvingMask.visit} column runs {@code (x, z, fromY, toY)}.
 * The carvers are loaded from the restored {@code data/} JSON with {@link CarverLoader}.
 */
class CarvingMaskGoldenTest {

    private static final Path DATA = Path.of("data");
    private static final WorldGenerationContext OVERWORLD = new WorldGenerationContext(-64, 384, 63);

    private static String describe(CarvingMask mask) {
        StringBuilder sb = new StringBuilder();
        int[] count = {0};
        mask.visit((x, z, from, to) -> {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(x).append(',').append(z).append(',').append(from).append(',').append(to);
            count[0] += to - from + 1;
        });
        return count[0] + "|" + sb;
    }

    @Test
    void masksMatchTheVanillaCarvers() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/carver")), "restored data/ needed");
        List<WorldCarver> all = List.of(CarverLoader.load(DATA, "cave"), CarverLoader.load(DATA, "cave_extra_underground"),
                CarverLoader.load(DATA, "canyon"));
        List<WorldCarver> canyonOnly = List.of(CarverLoader.load(DATA, "canyon"));
        List<String> lines;
        try (InputStream in = CarvingMaskGoldenTest.class.getResourceAsStream("carving_masks_golden.txt.gz")) {
            assertNotNull(in);
            lines = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)).lines()
                    .filter(l -> !l.isEmpty()).toList();
        }
        assertEquals(48, lines.size());
        int nonEmpty = 0;
        for (String line : lines) {
            String[] p = line.split("\\|", 5);
            long seed = Long.parseLong(p[1]);
            ChunkPos pos = new ChunkPos(Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            CarvingMask mask = CarverStep.computeMask(seed, pos, OVERWORLD, p[0].equals("all") ? all : canyonOnly);
            assertEquals(p[4], describe(mask), p[0] + " seed " + seed + " chunk " + pos);
            if (!mask.isEmpty()) {
                nonEmpty++;
            }
        }
        assertTrue(nonEmpty > 30);
    }

    @Test
    void maskRangeIsMinGenYPlusOneToTopMinusEight() {
        CarvingMask mask = CarverStep.newMask(OVERWORLD, false);
        assertEquals(-63, mask.minY());
        assertEquals(312, mask.maxY());
        CarvingMask upgrading = CarverStep.newMask(OVERWORLD, true);
        assertEquals(319, upgrading.maxY());
    }

    @Test
    void visitReportsColumnRuns() {
        CarvingMask mask = new CarvingMask(-63, 312);
        assertTrue(mask.isEmpty());
        mask.carve(3, 10, 5);
        mask.carve(3, 11, 5);
        mask.carve(3, 312, 5);
        mask.carve(3, -63, 6);
        mask.carve(15, 0, 15);
        assertEquals("5|3,5,10,11;3,5,312,312;3,6,-63,-63;15,15,0,0", describe(mask));
        assertTrue(mask.isCarved(3, 11, 5));
        assertEquals(5, mask.cardinality());
    }
}
