package com.example.worldgen.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Compares the {@link OverworldBiomeBuilder} and preset ports with the vanilla 26.3 oracle output. */
class OverworldBiomeBuilderTest {

    private static List<Climate.Entry<String>> build() {
        List<Climate.Entry<String>> entries = new ArrayList<>();
        new OverworldBiomeBuilder().addBiomes(entries::add);
        return entries;
    }

    @Test
    void everyEntryMatchesVanillaInOrder() throws Exception {
        List<Climate.Entry<String>> entries = build();
        List<String[]> golden = GoldenData.read("overworld_parameters.txt.gz");
        assertEquals(7594, golden.size());
        assertEquals(golden.size(), entries.size());
        for (int i = 0; i < entries.size(); i++) {
            assertEquals(String.join(" ", golden.get(i)), GoldenData.format(entries.get(i)), "entry " + i);
        }
    }

    @Test
    void usesFiftySixDistinctKnownBiomes() {
        Set<String> used = new LinkedHashSet<>();
        for (Climate.Entry<String> entry : build()) {
            used.add(entry.value());
            assertTrue(Biomes.ALL.contains(entry.value()), entry.value());
        }
        assertEquals(56, used.size());
        assertEquals(used, MultiNoiseBiomeSource.createFromPreset(
            new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD))
            .possibleBiomes());
    }

    @Test
    void netherPresetMatchesVanilla() throws Exception {
        List<String[]> golden = GoldenData.read("nether_parameters.txt.gz");
        List<Climate.Entry<String>> values = MultiNoiseBiomeSourceParameterList.Preset.NETHER.provider().get().values();
        assertEquals(5, golden.size());
        assertEquals(golden.size(), values.size());
        for (int i = 0; i < values.size(); i++) {
            assertEquals(String.join(" ", golden.get(i)), GoldenData.format(values.get(i)));
        }
    }

    @Test
    void biomesClassHasAllSixtySevenIdsInOrder() {
        assertEquals(67, Biomes.ALL.size());
        assertEquals(67, new LinkedHashSet<>(Biomes.ALL).size());
        assertEquals(Biomes.THE_VOID, Biomes.ALL.get(0));
        assertEquals(Biomes.END_BARRENS, Biomes.ALL.get(66));
    }

    @Test
    void biomeSourceResolvesThroughSampler() {
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromList(
            MultiNoiseBiomeSourceParameterList.Preset.NETHER.provider().get());
        Climate.DensitySamplerBound zero = (x, y, z) -> 0.0F;
        Climate.DensitySamplerBound wet = (x, y, z) -> 0.5F;
        assertEquals(Biomes.NETHER_WASTES,
            source.getNoiseBiome(0, 0, 0, new Climate.Sampler(zero, zero, zero, zero, zero, zero)));
        // humidity 0.5 matches warped forest's humidity point; its offset 0.375 (3750) still beats
        // nether wastes' humidity distance 5000.
        assertEquals(Biomes.WARPED_FOREST,
            source.getNoiseBiome(0, 0, 0, new Climate.Sampler(zero, wet, zero, zero, zero, zero)));
    }

    @Test
    void debugStringsUseQuantisedThresholds() {
        OverworldBiomeBuilder builder = new OverworldBiomeBuilder();
        assertEquals("Mushroom fields", builder.getDebugStringForContinentalness(-1.1));
        assertEquals("Coast", builder.getDebugStringForContinentalness(-0.15));
        assertEquals("Far inland", builder.getDebugStringForContinentalness(0.9));
        assertEquals("0", builder.getDebugStringForTemperature(-0.9));
        assertEquals("4", builder.getDebugStringForTemperature(0.9));
        assertEquals("?", builder.getDebugStringForTemperature(1.5));
        assertEquals("Valley", OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(-1.0));
        assertEquals("Peak", OverworldBiomeBuilder.getDebugStringForPeaksAndValleys(1.0));
        // TerrainProvider.peaksAndValleys(1/3) = -(|1/3 - 2/3| - 1/3) * 3 = 0 (within float rounding)
        assertEquals(0.0F, OverworldBiomeBuilder.peaksAndValleys(0.33333334F), 1.0E-6F);
        assertEquals(-1.0F, OverworldBiomeBuilder.peaksAndValleys(0.0F), 1.0E-6F);
        assertEquals(1.0F, OverworldBiomeBuilder.peaksAndValleys(0.6666667F), 1.0E-6F);
    }
}
