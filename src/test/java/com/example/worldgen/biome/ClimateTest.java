package com.example.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Tests for the {@link Climate} port (26.3 {@code net.minecraft.world.level.biome.Climate}). */
class ClimateTest {

    @Test
    void quantizeUsesFloatMultiplyThenTruncation() {
        assertEquals(10000L, Climate.quantizeCoord(1.0F));
        assertEquals(-4500L, Climate.quantizeCoord(-0.45F));
        // 0.26666668F * 10000.0F in float arithmetic is 2666.6667, truncated toward zero.
        assertEquals((long) (0.26666668F * 10000.0F), Climate.quantizeCoord(0.26666668F));
        assertEquals(2666L, Climate.quantizeCoord(0.26666668F));
        assertEquals(-2666L, Climate.quantizeCoord(-0.26666668F));
        assertEquals(0.5F, Climate.unquantizeCoord(5000L));
        Random random = new Random(1L);
        for (int i = 0; i < 10000; i++) {
            long q = random.nextInt(40001) - 20000;
            long back = Climate.quantizeCoord(Climate.unquantizeCoord(q));
            assertTrue(Math.abs(back - q) <= 1L, "round trip " + q + " -> " + back);
        }
        // Float rounding plus truncation is not an exact inverse: 6040 / 10000F * 10000F = 6039.9995F -> 6039.
        assertEquals(6039L, Climate.quantizeCoord(Climate.unquantizeCoord(6040L)));
    }

    @Test
    void parameterDistanceAndSpan() {
        Climate.Parameter p = Climate.Parameter.span(-0.5F, 0.25F);
        assertEquals(-5000L, p.min());
        assertEquals(2500L, p.max());
        assertEquals(0L, p.distance(0L));
        assertEquals(0L, p.distance(-5000L));
        assertEquals(100L, p.distance(2600L));
        assertEquals(1000L, p.distance(-6000L));
        assertEquals(p, p.span((Climate.Parameter) null));
        assertEquals(new Climate.Parameter(-5000L, 9000L), p.span(new Climate.Parameter(100L, 9000L)));
        assertThrows(IllegalArgumentException.class, () -> Climate.Parameter.span(0.5F, 0.1F));
        assertEquals("[-5000-2500]", p.toString());
        assertEquals("7", new Climate.Parameter(7L, 7L).toString());
    }

    @Test
    void fitnessIsSumOfSquaredDistancesPlusOffsetSquared() {
        Climate.ParameterPoint point = Climate.parameters(Climate.Parameter.span(0.0F, 0.1F),
            Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F),
            Climate.Parameter.point(0.0F), Climate.Parameter.point(0.0F), 0.01F);
        Climate.TargetPoint target = new Climate.TargetPoint(1100L, 3L, 0L, 0L, -4L, 0L);
        assertEquals(100L * 100L + 9L + 16L + 100L * 100L, point.fitness(target));
    }

    @Test
    void samplerConvertsQuartToBlockCoordinates() {
        List<int[]> calls = new ArrayList<>();
        Climate.DensitySamplerBound recorder = (x, y, z) -> {
            calls.add(new int[]{x, y, z});
            return 0.5F;
        };
        Climate.Sampler sampler = new Climate.Sampler(recorder, recorder, recorder, recorder, recorder, recorder);
        Climate.TargetPoint target = sampler.sample(3, -2, -5);
        assertEquals(new Climate.TargetPoint(5000L, 5000L, 5000L, 5000L, 5000L, 5000L), target);
        assertEquals(6, calls.size());
        for (int[] c : calls) {
            assertEquals(12, c[0]);
            assertEquals(-8, c[1]);
            assertEquals(-20, c[2]);
        }
    }

    @Test
    void rTreeSearchMatchesVanillaOracleInQueryOrder() throws Exception {
        Climate.ParameterList<String> list = MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD.provider().get();
        List<String[]> rows = GoldenData.read("overworld_search.txt.gz");
        assertEquals(5000, rows.size());
        int indexDiffersFromBruteForce = 0;
        for (String[] row : rows) {
            Climate.TargetPoint target = GoldenData.target(row);
            assertEquals(row[6], list.findValue(target), "index search for " + target);
            assertEquals(row[7], list.findValueBruteForce(target), "brute force for " + target);
            if (!row[6].equals(row[7])) {
                indexDiffersFromBruteForce++;
            }
        }
        // The vanilla R-tree with lastResult reuse is not exact; the oracle recorded these disagreements.
        assertEquals(84, indexDiffersFromBruteForce);
    }

    @Test
    void netherSearchMatchesVanillaOracle() throws Exception {
        Climate.ParameterList<String> list = MultiNoiseBiomeSourceParameterList.Preset.NETHER.provider().get();
        for (String[] row : GoldenData.read("nether_search.txt.gz")) {
            Climate.TargetPoint target = GoldenData.target(row);
            assertEquals(row[6], list.findValue(target));
            assertEquals(row[7], list.findValueBruteForce(target));
        }
    }

    @Test
    void emptyListIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Climate.ParameterList<String>(List.of()));
    }
}
